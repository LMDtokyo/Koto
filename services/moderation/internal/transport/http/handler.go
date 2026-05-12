package http

import (
	"context"
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/koto-messenger/koto/services/moderation/internal/app"
	"github.com/koto-messenger/koto/services/moderation/internal/domain"
)

const accountIDKey contextKey = "account_id"

type contextKey string

type Handler struct {
	svc *app.Service
}

func New(svc *app.Service) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) Router() chi.Router {
	r := chi.NewRouter()
	r.Use(h.accountIDMiddleware)

	r.Post("/v1/moderation/report", h.submitReport)
	r.Get("/v1/moderation/pending", h.listPending) // только для админ-панели
	r.Get("/v1/moderation/account/{accountID}", h.accountStatus)

	return r
}

// accountIDMiddleware читает X-Account-ID, который проставляет gateway после JWT-валидации.
func (h *Handler) accountIDMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := r.Header.Get("X-Account-ID")
		if id != "" {
			r = r.WithContext(context.WithValue(r.Context(), accountIDKey, id))
		}
		next.ServeHTTP(w, r)
	})
}

func (h *Handler) submitReport(w http.ResponseWriter, r *http.Request) {
	reporterID, _ := r.Context().Value(accountIDKey).(string)
	if reporterID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var body struct {
		ReportedID     string                  `json:"reported_id"`
		ConversationID string                  `json:"conversation_id"`
		MessageID      string                  `json:"message_id"`
		Reason         string                  `json:"reason"`
		Plaintext      string                  `json:"plaintext"`
		Context        []domain.ContextMessage `json:"context"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "invalid body")
		return
	}
	if body.ReportedID == "" {
		writeError(w, http.StatusBadRequest, "reported_id required")
		return
	}
	if body.Reason == "" {
		body.Reason = "other"
	}

	report, err := h.svc.SubmitReport(r.Context(), app.SubmitReportInput{
		ReporterID:     reporterID,
		ReportedID:     body.ReportedID,
		ConversationID: body.ConversationID,
		MessageID:      body.MessageID,
		Reason:         body.Reason,
		Plaintext:      body.Plaintext,
		Context:        body.Context,
	})
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusCreated, map[string]any{
		"id":             report.ID,
		"status":         report.Status,
		"classification": report.Classification,
	})
}

func (h *Handler) listPending(w http.ResponseWriter, r *http.Request) {
	// TODO: проверка, что пользователь — модератор. Пока открыто для всех authenticated.
	reports, err := h.svc.ListPending(r.Context(), 50)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": reports})
}

func (h *Handler) accountStatus(w http.ResponseWriter, r *http.Request) {
	accountID := chi.URLParam(r, "accountID")
	action, err := h.svc.CheckAccountStatus(r.Context(), accountID)
	if err != nil {
		// Если записи нет — аккаунт чистый.
		writeJSON(w, http.StatusOK, map[string]any{"account_id": accountID, "action": "none"})
		return
	}
	writeJSON(w, http.StatusOK, action)
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}
