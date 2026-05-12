// Package http provides the HTTP transport layer for the auth service.
package http

import (
	"encoding/hex"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/pkg/logger"
	"github.com/koto-messenger/koto/services/auth/internal/app"
	"github.com/rs/zerolog"
)

// Handler holds HTTP handlers for the auth service.
type Handler struct {
	svc *app.Service
	log zerolog.Logger
}

// NewHandler creates a new HTTP handler.
func NewHandler(svc *app.Service, log zerolog.Logger) *Handler {
	return &Handler{svc: svc, log: log}
}

// Router builds and returns the chi router for the auth service.
func (h *Handler) Router() http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Recoverer)
	r.Use(requestLoggerMiddleware(h.log))

	r.Get("/health", h.Health)

	r.Route("/v1/auth", func(r chi.Router) {
		r.Post("/register",       h.Register)
		r.Post("/restore",        h.Restore)
		r.Post("/token/refresh",  h.RefreshToken)
		r.Post("/token/revoke",   h.RevokeToken)
		r.Post("/token/validate", h.ValidateToken)

		r.Get   ("/sessions",            h.ListSessions)
		r.Delete("/sessions/{sessionID}", h.RevokeSession)

		r.Route("/prekeys", func(r chi.Router) {
			r.Post("/publish",    h.PublishPreKeys)
			r.Get("/bundle/{accountID}", h.FetchPreKeyBundle)
		})
	})

	return r
}

// ─── handlers ────────────────────────────────────────────────────────────────

func (h *Handler) Health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// Register creates a new anonymous account.
// POST /v1/auth/register
// Keys are accepted as lowercase hex strings.
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
	in, err := decodeKeyMaterial(r)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	pair, err := h.svc.Register(r.Context(), in)
	if err != nil {
		writeAppError(w, err)
		return
	}
	writeTokenPair(w, http.StatusCreated, pair)
}

// Restore re-establishes a session for an existing account whose owner can
// produce a fresh XEdDSA signature with the matching identity private key.
// POST /v1/auth/restore — same body shape as /register.
func (h *Handler) Restore(w http.ResponseWriter, r *http.Request) {
	in, err := decodeKeyMaterial(r)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	pair, err := h.svc.Restore(r.Context(), in)
	if err != nil {
		writeAppError(w, err)
		return
	}
	writeTokenPair(w, http.StatusOK, pair)
}

func decodeKeyMaterial(r *http.Request) (app.RegisterInput, error) {
	var body struct {
		IdentityKey     string   `json:"identity_key"`
		SignedPreKey    string   `json:"signed_pre_key"`
		SignedPreKeySig string   `json:"signed_pre_key_sig"`
		SignedPreKeyID  uint32   `json:"signed_pre_key_id"`
		OneTimePreKeys  []string `json:"one_time_pre_keys"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		return app.RegisterInput{}, errors.New("invalid JSON body")
	}
	identityKey, err := hex.DecodeString(body.IdentityKey)
	if err != nil {
		return app.RegisterInput{}, errors.New("identity_key must be hex")
	}
	signedPreKey, err := hex.DecodeString(body.SignedPreKey)
	if err != nil {
		return app.RegisterInput{}, errors.New("signed_pre_key must be hex")
	}
	signedPreKeySig, err := hex.DecodeString(body.SignedPreKeySig)
	if err != nil {
		return app.RegisterInput{}, errors.New("signed_pre_key_sig must be hex")
	}
	otks := make([][]byte, 0, len(body.OneTimePreKeys))
	for _, k := range body.OneTimePreKeys {
		kb, err := hex.DecodeString(k)
		if err != nil {
			return app.RegisterInput{}, errors.New("one_time_pre_keys must be hex")
		}
		otks = append(otks, kb)
	}
	return app.RegisterInput{
		IdentityKey:     identityKey,
		SignedPreKey:    signedPreKey,
		SignedPreKeySig: signedPreKeySig,
		SignedPreKeyID:  body.SignedPreKeyID,
		OneTimePreKeys:  otks,
		Device:          deviceInfoFromRequest(r),
	}, nil
}

// deviceInfoFromRequest captures the optional X-Device-* headers + the
// observed client IP. RealIP middleware (registered upstream) replaces
// RemoteAddr with X-Forwarded-For when present, so this is the truthful
// origin even behind the gateway.
func deviceInfoFromRequest(r *http.Request) app.DeviceInfo {
	ip := r.RemoteAddr
	if i := strings.LastIndex(ip, ":"); i > 0 {
		ip = ip[:i] // drop port
	}
	return app.DeviceInfo{
		Name:       trimHeader(r.Header.Get("X-Device-Name"), 64),
		Platform:   trimHeader(r.Header.Get("X-Platform"), 32),
		AppVersion: trimHeader(r.Header.Get("X-App-Version"), 32),
		ClientIP:   ip,
	}
}

func trimHeader(s string, max int) string {
	s = strings.TrimSpace(s)
	if len(s) > max {
		return s[:max]
	}
	return s
}

func writeTokenPair(w http.ResponseWriter, status int, pair app.TokenPair) {
	writeJSON(w, status, map[string]any{
		"account_id":    pair.AccountID,
		"session_id":    pair.SessionID,
		"access_token":  pair.AccessToken,
		"refresh_token": pair.RefreshToken,
		"expires_at":    pair.ExpiresAt.Unix(),
	})
}

// ListSessions returns the active devices for the caller.
// GET /v1/auth/sessions  (X-Account-ID set by gateway)
func (h *Handler) ListSessions(w http.ResponseWriter, r *http.Request) {
	accountID := r.Header.Get("X-Account-ID")
	if accountID == "" {
		writeError(w, http.StatusUnauthorized, "X-Account-ID header required")
		return
	}
	sessions, err := h.svc.ListSessions(r.Context(), accountID)
	if err != nil {
		writeAppError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"sessions": sessions})
}

// RevokeSession signs the named device out remotely.
// DELETE /v1/auth/sessions/{sessionID}
func (h *Handler) RevokeSession(w http.ResponseWriter, r *http.Request) {
	accountID := r.Header.Get("X-Account-ID")
	if accountID == "" {
		writeError(w, http.StatusUnauthorized, "X-Account-ID header required")
		return
	}
	sessionID := chi.URLParam(r, "sessionID")
	if err := h.svc.RevokeSession(r.Context(), accountID, sessionID); err != nil {
		writeAppError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// RefreshToken issues new tokens from a refresh token.
// POST /v1/auth/token/refresh
func (h *Handler) RefreshToken(w http.ResponseWriter, r *http.Request) {
	var body struct {
		RefreshToken string `json:"refresh_token"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.RefreshToken == "" {
		writeError(w, http.StatusBadRequest, "refresh_token required")
		return
	}

	pair, err := h.svc.RefreshTokens(r.Context(), body.RefreshToken, deviceInfoFromRequest(r))
	if err != nil {
		writeAppError(w, err)
		return
	}
	writeTokenPair(w, http.StatusOK, pair)
}

// RevokeToken invalidates a refresh token (logout).
// POST /v1/auth/token/revoke
func (h *Handler) RevokeToken(w http.ResponseWriter, r *http.Request) {
	var body struct {
		RefreshToken string `json:"refresh_token"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.RefreshToken == "" {
		writeError(w, http.StatusBadRequest, "refresh_token required")
		return
	}

	if err := h.svc.RevokeToken(r.Context(), body.RefreshToken); err != nil {
		writeAppError(w, err)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// ValidateToken verifies an access token (used internally by gateway).
// POST /v1/auth/token/validate
func (h *Handler) ValidateToken(w http.ResponseWriter, r *http.Request) {
	var body struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.AccessToken == "" {
		writeError(w, http.StatusBadRequest, "access_token required")
		return
	}

	accountID, err := h.svc.ValidateToken(r.Context(), body.AccessToken)
	if err != nil {
		writeJSON(w, http.StatusOK, map[string]any{"valid": false, "account_id": ""})
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{"valid": true, "account_id": accountID})
}

// PublishPreKeys adds new one-time pre-keys for an account.
// POST /v1/auth/prekeys/publish
func (h *Handler) PublishPreKeys(w http.ResponseWriter, r *http.Request) {
	accountID := r.Header.Get("X-Account-ID")
	if accountID == "" {
		writeError(w, http.StatusUnauthorized, "X-Account-ID header required")
		return
	}

	var body struct {
		Keys []string `json:"keys"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || len(body.Keys) == 0 {
		writeError(w, http.StatusBadRequest, "keys required")
		return
	}
	var keys [][]byte
	for _, k := range body.Keys {
		kb, err := hex.DecodeString(k)
		if err != nil {
			writeError(w, http.StatusBadRequest, "keys must be hex")
			return
		}
		keys = append(keys, kb)
	}

	total, err := h.svc.PublishPreKeys(r.Context(), accountID, keys)
	if err != nil {
		writeAppError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, map[string]int{"total": total})
}

// FetchPreKeyBundle retrieves key material for starting an X3DH session.
// GET /v1/auth/prekeys/bundle/{accountID}
func (h *Handler) FetchPreKeyBundle(w http.ResponseWriter, r *http.Request) {
	targetID := chi.URLParam(r, "accountID")

	acc, otk, err := h.svc.FetchPreKeyBundle(r.Context(), targetID)
	if err != nil {
		writeAppError(w, err)
		return
	}

	resp := map[string]any{
		"account_id":          acc.ID,
		"identity_key":        acc.IdentityKey,
		"signed_pre_key":      acc.SignedPreKey,
		"signed_pre_key_sig":  acc.SignedPreKeySig,
		"signed_pre_key_id":   acc.SignedPreKeyID,
	}
	if len(otk.KeyData) > 0 {
		resp["one_time_pre_key"]    = otk.KeyData
		resp["one_time_pre_key_id"] = otk.ID
	}

	writeJSON(w, http.StatusOK, resp)
}

// ─── helpers ─────────────────────────────────────────────────────────────────

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

func writeAppError(w http.ResponseWriter, err error) {
	status := apperrors.HTTPStatus(err)
	writeJSON(w, status, map[string]string{"error": err.Error()})
}

func requestLoggerMiddleware(log zerolog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ctx := logger.WithCtx(r.Context(), log.With().
				Str("req_id", middleware.GetReqID(r.Context())).
				Str("method", r.Method).
				Str("path", r.URL.Path).
				Logger())
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}
