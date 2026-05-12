// Package http provides HTTP handlers for the chat service.
package http

import (
	"context"
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/pkg/logger"
	"github.com/koto-messenger/koto/services/chat/internal/app"
	"github.com/koto-messenger/koto/services/chat/internal/domain"
	"github.com/rs/zerolog"
)

// Handler provides HTTP endpoints for the chat service.
type Handler struct {
	svc *app.Service
	log zerolog.Logger
}

func NewHandler(svc *app.Service, log zerolog.Logger) *Handler {
	return &Handler{svc: svc, log: log}
}

// Router builds the chi router.
func (h *Handler) Router() http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Recoverer)
	r.Use(requestLoggerMiddleware(h.log))
	r.Use(accountIDMiddleware)

	r.Get("/health", h.Health)

	r.Route("/v1/conversations", func(r chi.Router) {
		r.Post("/",    h.CreateConversation)
		r.Get("/",     h.GetConversations)
		r.Get("/search", h.SearchConversations)
		r.Route("/{convID}", func(r chi.Router) {
			r.Post("/messages",            h.SendMessage)
			r.Get("/messages",             h.GetHistory)
			r.Get("/messages/search",      h.SearchMessagesMeta)
			r.Patch("/messages/{msgID}",   h.EditMessage)
			r.Delete("/messages/{msgID}",  h.DeleteMessage)
			// Reactions on a single message. Toggle is idempotent: same
			// actor+emoji POSTed twice removes the reaction the second time.
			r.Get ("/messages/{msgID}/reactions",            h.ListReactions)
			r.Post("/messages/{msgID}/reactions/{emoji}",    h.ToggleReaction)
			// Pin / unpin and list of currently-pinned messages.
			r.Get ("/pinned",                                h.ListPinned)
			r.Post("/messages/{msgID}/pin",                  h.PinMessage)
			r.Delete("/messages/{msgID}/pin",                h.UnpinMessage)
		})
	})

	return r
}

// GET /v1/conversations/search?q=&limit=&cursor=
func (h *Handler) SearchConversations(w http.ResponseWriter, r *http.Request) {
	memberID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || memberID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	res, err := h.svc.SearchConversations(r.Context(), app.SearchConversationsInput{
		MemberID: memberID,
		Query:    r.URL.Query().Get("q"),
		Limit:    atoiDefault(r.URL.Query().Get("limit"), 20),
		Cursor:   r.URL.Query().Get("cursor"),
	})
	if err != nil {
		writeAppError(w, err)
		return
	}
	resp := make([]convResp, 0, len(res.Items))
	for _, c := range res.Items {
		peerID := ""
		if c.Type == domain.ConversationTypeDirect {
			for _, id := range c.MemberIDs {
				if id != memberID {
					peerID = id
					break
				}
			}
		}
		displayName := c.Name
		if c.Type == domain.ConversationTypeDirect {
			displayName = peerID
		}
		resp = append(resp, convResp{
			ID:          c.ID,
			Type:        uint8(c.Type),
			Name:        c.Name,
			DisplayName: displayName,
			PeerID:      peerID,
			MemberIDs:   c.MemberIDs,
			LastMessage: nil,
			UnreadCount: 0,
			Online:      false,
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"items":       resp,
		"next_cursor": res.NextCursor,
		"has_more":    res.HasMore,
	})
}

func (h *Handler) Health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// POST /v1/conversations
func (h *Handler) CreateConversation(w http.ResponseWriter, r *http.Request) {
	creatorID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || creatorID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var body struct {
		MemberIDs []string `json:"member_ids"`
		Type      uint8    `json:"type"` // 1=direct, 2=group
		Name      string   `json:"name"` // group label (empty for direct)
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "invalid body")
		return
	}

	conv, err := h.svc.CreateConversation(
		r.Context(),
		creatorID,
		body.MemberIDs,
		domain.ConversationType(body.Type),
		body.Name,
	)
	if err != nil {
		writeAppError(w, err)
		return
	}

	writeJSON(w, http.StatusCreated, map[string]any{
		"conversation_id": conv.ID,
		"type":            uint8(conv.Type),
		"name":            conv.Name,
		"member_ids":      conv.MemberIDs,
	})
}

// GET /v1/conversations
func (h *Handler) GetConversations(w http.ResponseWriter, r *http.Request) {
	memberID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || memberID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	convs, err := h.svc.GetConversations(r.Context(), memberID)
	if err != nil {
		writeAppError(w, err)
		return
	}

	resp := make([]convResp, 0, len(convs))
	for _, c := range convs {
		// For a direct conversation, the peer is the one non-self member —
		// the client renders that peer's profile name. For a group, peer_id
		// stays empty and display_name carries the user-supplied label.
		peerID := ""
		if c.Type == domain.ConversationTypeDirect {
			for _, id := range c.MemberIDs {
				if id != memberID {
					peerID = id
					break
				}
			}
		}
		displayName := c.Name
		if c.Type == domain.ConversationTypeDirect {
			displayName = peerID
		}

		// Fetch the newest message so the client can show a preview in the
		// conversation list instead of "No messages". 1-deep query only.
		var lastMsg *msgResp
		if history, herr := h.svc.GetHistory(r.Context(), c.ID, memberID, "", 1); herr == nil && len(history) > 0 {
			m := history[0]
			var editedUnix int64
			if m.EditedAt != nil {
				editedUnix = m.EditedAt.Unix()
			}
			lastMsg = &msgResp{
				ID:            m.ID,
				Ciphertext:    m.Ciphertext,
				SenderID:      m.SenderID,
				SentAt:        m.SentAt.Unix(),
				Delivered:     false,
				ReplyTo:       m.ReplyTo,
				EditedAt:      editedUnix,
				ForwardedFrom: m.ForwardedFrom,
			}
		}

		resp = append(resp, convResp{
			ID:          c.ID,
			Type:        uint8(c.Type),
			Name:        c.Name,
			DisplayName: displayName,
			PeerID:      peerID,
			MemberIDs:   c.MemberIDs,
			LastMessage: lastMsg,
			UnreadCount: 0,
			Online:      false,
		})
	}

	writeJSON(w, http.StatusOK, resp)
}

// ── Response DTOs (Android-compatible field names + types) ───────────────────

type convResp struct {
	ID          string   `json:"id"`
	Type        uint8    `json:"type"` // 1=direct, 2=group
	Name        string   `json:"name"` // user-supplied group label, empty for direct
	DisplayName string   `json:"display_name"`
	PeerID      string   `json:"peer_id"`    // direct convs only: the other member's account ID
	MemberIDs   []string `json:"member_ids"` // full membership (group convs use this for avatar fan-out)
	LastMessage *msgResp `json:"last_message"`
	UnreadCount int      `json:"unread_count"`
	Online      bool     `json:"online"`
}

type msgResp struct {
	ID            string `json:"id"`
	Ciphertext    []byte `json:"ciphertext"` // encoding/json base64-encodes []byte automatically
	SenderID      string `json:"sender_id"`
	SentAt        int64  `json:"sent_at"` // Unix seconds — Android expects Long
	Delivered     bool   `json:"delivered"`
	ReplyTo       string `json:"reply_to,omitempty"`
	EditedAt      int64  `json:"edited_at,omitempty"` // Unix seconds; 0 = never edited
	ForwardedFrom string `json:"forwarded_from,omitempty"`
	Pinned        bool   `json:"pinned,omitempty"`
}

// POST /v1/conversations/{convID}/messages
func (h *Handler) SendMessage(w http.ResponseWriter, r *http.Request) {
	senderID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || senderID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")

	var body struct {
		Type          uint8  `json:"type"`
		Ciphertext    []byte `json:"ciphertext"`
		SenderKeyData []byte `json:"sender_key_data"`
		ClientSeq     int64  `json:"client_seq"`
		ReplyTo       string `json:"reply_to"`
		ForwardedFrom string `json:"forwarded_from"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "invalid body")
		return
	}

	msg, err := h.svc.SendMessage(r.Context(), app.SendInput{
		ConversationID: convID,
		SenderID:       senderID,
		Type:           domain.MessageType(body.Type),
		Ciphertext:     body.Ciphertext,
		SenderKeyData:  body.SenderKeyData,
		ClientSeq:      body.ClientSeq,
		ReplyTo:        body.ReplyTo,
		ForwardedFrom:  body.ForwardedFrom,
	})
	if err != nil {
		writeAppError(w, err)
		return
	}

	writeJSON(w, http.StatusCreated, map[string]any{
		"id":      msg.ID,
		"sent_at": msg.SentAt.Unix(),
	})
}

// GET /v1/conversations/{convID}/messages?cursor=&limit=
func (h *Handler) GetHistory(w http.ResponseWriter, r *http.Request) {
	senderID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || senderID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")
	cursor   := r.URL.Query().Get("cursor")

	limit := 50
	if l := r.URL.Query().Get("limit"); l != "" {
		if n, err := strconv.Atoi(l); err == nil {
			limit = n
		}
	}

	msgs, err := h.svc.GetHistory(r.Context(), convID, senderID, cursor, limit)
	if err != nil {
		writeAppError(w, err)
		return
	}

	resp := make([]msgResp, 0, len(msgs))
	for _, m := range msgs {
		var editedUnix int64
		if m.EditedAt != nil {
			editedUnix = m.EditedAt.Unix()
		}
		resp = append(resp, msgResp{
			ID:            m.ID,
			Ciphertext:    m.Ciphertext,
			SenderID:      m.SenderID,
			SentAt:        m.SentAt.Unix(),
			Delivered:     false,
			ReplyTo:       m.ReplyTo,
			EditedAt:      editedUnix,
			ForwardedFrom: m.ForwardedFrom,
			Pinned:        m.Pinned,
		})
	}
	writeJSON(w, http.StatusOK, resp)
}

// GET /v1/conversations/{convID}/messages/search
func (h *Handler) SearchMessagesMeta(w http.ResponseWriter, r *http.Request) {
	requesterID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || requesterID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")
	var (
		from *time.Time
		to   *time.Time
		mt   *domain.MessageType
	)
	if raw := r.URL.Query().Get("from"); raw != "" {
		if ts, err := strconv.ParseInt(raw, 10, 64); err == nil {
			t := time.Unix(ts, 0).UTC()
			from = &t
		}
	}
	if raw := r.URL.Query().Get("to"); raw != "" {
		if ts, err := strconv.ParseInt(raw, 10, 64); err == nil {
			t := time.Unix(ts, 0).UTC()
			to = &t
		}
	}
	if raw := r.URL.Query().Get("type"); raw != "" {
		if n, err := strconv.Atoi(raw); err == nil {
			v := domain.MessageType(n)
			mt = &v
		}
	}
	res, err := h.svc.SearchMessagesMeta(r.Context(), app.SearchMessagesMetaInput{
		ConversationID: convID,
		RequesterID:    requesterID,
		SenderID:       r.URL.Query().Get("sender_id"),
		From:           from,
		To:             to,
		Type:           mt,
		Limit:          atoiDefault(r.URL.Query().Get("limit"), 50),
		Cursor:         r.URL.Query().Get("cursor"),
	})
	if err != nil {
		writeAppError(w, err)
		return
	}
	out := make([]msgResp, 0, len(res.Items))
	for _, m := range res.Items {
		var editedUnix int64
		if m.EditedAt != nil {
			editedUnix = m.EditedAt.Unix()
		}
		out = append(out, msgResp{
			ID:            m.ID,
			Ciphertext:    m.Ciphertext,
			SenderID:      m.SenderID,
			SentAt:        m.SentAt.Unix(),
			Delivered:     false,
			ReplyTo:       m.ReplyTo,
			EditedAt:      editedUnix,
			ForwardedFrom: m.ForwardedFrom,
			Pinned:        m.Pinned,
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"items":       out,
		"next_cursor": res.NextCursor,
		"has_more":    res.NextCursor != "",
	})
}

// POST /v1/conversations/{convID}/messages/{msgID}/pin
func (h *Handler) PinMessage(w http.ResponseWriter, r *http.Request) {
	actorID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || actorID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")
	msgID  := chi.URLParam(r, "msgID")
	if err := h.svc.SetPinned(r.Context(), convID, msgID, actorID, true); err != nil {
		writeAppError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// DELETE /v1/conversations/{convID}/messages/{msgID}/pin
func (h *Handler) UnpinMessage(w http.ResponseWriter, r *http.Request) {
	actorID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || actorID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")
	msgID  := chi.URLParam(r, "msgID")
	if err := h.svc.SetPinned(r.Context(), convID, msgID, actorID, false); err != nil {
		writeAppError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// GET /v1/conversations/{convID}/pinned
func (h *Handler) ListPinned(w http.ResponseWriter, r *http.Request) {
	requesterID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || requesterID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")
	msgs, err := h.svc.ListPinned(r.Context(), convID, requesterID)
	if err != nil {
		writeAppError(w, err)
		return
	}
	resp := make([]msgResp, 0, len(msgs))
	for _, m := range msgs {
		var editedUnix int64
		if m.EditedAt != nil {
			editedUnix = m.EditedAt.Unix()
		}
		resp = append(resp, msgResp{
			ID:            m.ID,
			Ciphertext:    m.Ciphertext,
			SenderID:      m.SenderID,
			SentAt:        m.SentAt.Unix(),
			Delivered:     false,
			ReplyTo:       m.ReplyTo,
			EditedAt:      editedUnix,
			ForwardedFrom: m.ForwardedFrom,
			Pinned:        true,
		})
	}
	writeJSON(w, http.StatusOK, resp)
}

// PATCH /v1/conversations/{convID}/messages/{msgID}
// Body: { "ciphertext": "..." }. Sender-only.
func (h *Handler) EditMessage(w http.ResponseWriter, r *http.Request) {
	requesterID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || requesterID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")
	msgID  := chi.URLParam(r, "msgID")

	var body struct {
		Ciphertext []byte `json:"ciphertext"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "invalid body")
		return
	}

	editedAt, err := h.svc.EditMessage(r.Context(), convID, msgID, requesterID, body.Ciphertext)
	if err != nil {
		writeAppError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"edited_at": editedAt.Unix()})
}

// POST /v1/conversations/{convID}/messages/{msgID}/reactions/{emoji}
// Toggle: the same actor+emoji creates the reaction the first time, removes
// it the second. Response: {"added": bool}.
func (h *Handler) ToggleReaction(w http.ResponseWriter, r *http.Request) {
	actorID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || actorID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")
	msgID  := chi.URLParam(r, "msgID")
	// chi rebuilds the path arg already URL-decoded.
	emoji  := chi.URLParam(r, "emoji")

	added, err := h.svc.ToggleReaction(r.Context(), convID, msgID, actorID, emoji)
	if err != nil {
		writeAppError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"added": added})
}

// GET /v1/conversations/{convID}/messages/{msgID}/reactions
func (h *Handler) ListReactions(w http.ResponseWriter, r *http.Request) {
	requesterID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || requesterID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")
	msgID  := chi.URLParam(r, "msgID")

	reactions, err := h.svc.ListReactions(r.Context(), convID, msgID, requesterID)
	if err != nil {
		writeAppError(w, err)
		return
	}
	resp := make([]reactionResp, 0, len(reactions))
	for _, x := range reactions {
		resp = append(resp, reactionResp{
			ActorID:   x.ActorID,
			Emoji:     x.Emoji,
			ReactedAt: x.ReactedAt.Unix(),
		})
	}
	writeJSON(w, http.StatusOK, resp)
}

type reactionResp struct {
	ActorID   string `json:"actor_id"`
	Emoji     string `json:"emoji"`
	ReactedAt int64  `json:"reacted_at"`
}

// DELETE /v1/conversations/{convID}/messages/{msgID}
func (h *Handler) DeleteMessage(w http.ResponseWriter, r *http.Request) {
	senderID, ok := r.Context().Value(accountIDKey).(string)
	if !ok || senderID == "" {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	convID := chi.URLParam(r, "convID")
	msgID  := chi.URLParam(r, "msgID")

	if err := h.svc.DeleteMessage(r.Context(), convID, msgID, senderID); err != nil {
		writeAppError(w, err)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// ─── helpers ─────────────────────────────────────────────────────────────────

type contextKey string

const accountIDKey contextKey = "account_id"

// accountIDMiddleware reads X-Account-ID injected by the gateway.
func accountIDMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := r.Header.Get("X-Account-ID")
		if id == "" {
			writeError(w, http.StatusUnauthorized, "X-Account-ID header missing")
			return
		}
		ctx := r.Context()
		ctx = context.WithValue(ctx, accountIDKey, id)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
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

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

func writeAppError(w http.ResponseWriter, err error) {
	writeJSON(w, apperrors.HTTPStatus(err), map[string]string{"error": err.Error()})
}

func atoiDefault(raw string, fallback int) int {
	if raw == "" {
		return fallback
	}
	n, err := strconv.Atoi(raw)
	if err != nil {
		return fallback
	}
	return n
}
