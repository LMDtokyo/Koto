package main

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/coder/websocket"
	"github.com/go-chi/chi/v5"
	chimw "github.com/go-chi/chi/v5/middleware"
	nats "github.com/nats-io/nats.go"
	"github.com/koto-messenger/koto/pkg/logger"
	"github.com/koto-messenger/koto/pkg/token"
	gwmw "github.com/koto-messenger/koto/services/gateway/internal/middleware"
	"github.com/koto-messenger/koto/services/gateway/internal/proxy"
	"github.com/koto-messenger/koto/services/gateway/internal/ws"
	"github.com/rs/zerolog"
)

var version = "dev"

// deliveryEvent mirrors chat/internal/domain.DeliveryEvent — copied here to
// avoid a cross-service import; field names must stay in sync.
type deliveryEvent struct {
	MessageID      string    `json:"msg_id"`
	ConversationID string    `json:"conv_id"`
	SenderID       string    `json:"sender_id"`
	RecipientIDs   []string  `json:"recipient_ids"`
	Ciphertext     []byte    `json:"ciphertext"`
	SentAt         time.Time `json:"sent_at"`
	ReplyTo        string    `json:"reply_to,omitempty"`
	ForwardedFrom  string    `json:"forwarded_from,omitempty"`
}

// conversationCreatedEvent mirrors chat/internal/domain.ConversationCreatedEvent.
type conversationCreatedEvent struct {
	ConversationID string    `json:"conv_id"`
	Type           uint8     `json:"type"`
	Name           string    `json:"name"`
	CreatorID      string    `json:"creator_id"`
	MemberIDs      []string  `json:"member_ids"`
	RecipientIDs   []string  `json:"recipient_ids"`
	CreatedAt      time.Time `json:"created_at"`
}

// pinEvent mirrors chat/internal/domain.PinEvent.
type pinEvent struct {
	ConversationID string    `json:"conv_id"`
	MessageID      string    `json:"msg_id"`
	ActorID        string    `json:"actor_id"`
	Pinned         bool      `json:"pinned"`
	RecipientIDs   []string  `json:"recipient_ids"`
	At             time.Time `json:"at"`
}

// editEvent mirrors chat/internal/domain.EditEvent.
type editEvent struct {
	MessageID      string    `json:"msg_id"`
	ConversationID string    `json:"conv_id"`
	SenderID       string    `json:"sender_id"`
	RecipientIDs   []string  `json:"recipient_ids"`
	Ciphertext     []byte    `json:"ciphertext"`
	EditedAt       time.Time `json:"edited_at"`
}

// reactionEvent mirrors chat/internal/domain.ReactionEvent.
type reactionEvent struct {
	ConversationID string    `json:"conv_id"`
	MessageID      string    `json:"msg_id"`
	ActorID        string    `json:"actor_id"`
	Emoji          string    `json:"emoji"`
	Added          bool      `json:"added"`
	RecipientIDs   []string  `json:"recipient_ids"`
	ReactedAt      time.Time `json:"reacted_at"`
}

func main() {
	log := logger.New("gateway", version, os.Getenv("LOG_LEVEL") == "debug")

	// ─── Token manager (verify-only — gateway has no private key) ────────────
	tokenMgr, err := token.NewManagerFromPublicKey(mustEnv("JWT_PUBLIC_KEY"))
	if err != nil {
		log.Fatal().Err(err).Msg("token manager init failed")
	}

	// ─── Upstream proxies ─────────────────────────────────────────────────────
	upstreams := map[string]*proxy.Upstream{}
	for name, envKey := range map[string]string{
		"auth":         "AUTH_ADDR",
		"chat":         "CHAT_ADDR",
		"user":         "USER_ADDR",
		"media":        "MEDIA_ADDR",
		"bot":          "BOT_ADDR",
		"notification": "NOTIFICATION_ADDR",
		"moderation":   "MODERATION_ADDR",
	} {
		addr := mustEnv(envKey)
		up, err := proxy.NewUpstream(addr)
		if err != nil {
			log.Fatal().Err(err).Str("service", name).Msg("upstream init failed")
		}
		upstreams[name] = up
		log.Info().Str("service", name).Str("addr", addr).Msg("upstream registered")
	}

	// ─── WebSocket hub ────────────────────────────────────────────────────────
	hub := ws.NewHub(log)

	// ─── NATS: subscribe to chat delivery events ──────────────────────────────
	// Chat service publishes to "chat.deliver.{convId}" after each message.
	// We subscribe here and push a WebSocket frame to every online recipient.
	natsURL := env("NATS_URL", nats.DefaultURL)
	nc, err := nats.Connect(natsURL,
		nats.RetryOnFailedConnect(true),
		nats.MaxReconnects(-1),
		nats.ReconnectWait(2*time.Second),
		nats.DisconnectErrHandler(func(_ *nats.Conn, err error) {
			log.Warn().Err(err).Msg("nats disconnected")
		}),
		nats.ReconnectHandler(func(_ *nats.Conn) {
			log.Info().Msg("nats reconnected")
		}),
	)
	if err != nil {
		log.Fatal().Err(err).Str("url", natsURL).Msg("nats connect failed")
	}
	defer nc.Drain()

	_, err = nc.Subscribe("chat.deliver.>", func(msg *nats.Msg) {
		var event deliveryEvent
		if err := json.Unmarshal(msg.Data, &event); err != nil {
			log.Error().Err(err).Msg("failed to unmarshal delivery event")
			return
		}

		payload := map[string]any{
			"conversation_id": event.ConversationID,
			"message_id":      event.MessageID,
			"sender_id":       event.SenderID,
			// []byte is base64-encoded by encoding/json; Android base64-decodes it.
			"ciphertext":     event.Ciphertext,
			"sent_at":        event.SentAt.Unix(),
			"reply_to":       event.ReplyTo,
			"forwarded_from": event.ForwardedFrom,
		}

		for _, recipientID := range event.RecipientIDs {
			n := hub.Send(recipientID, ws.Envelope{
				Type:    "new_message",
				Payload: payload,
			})
			log.Debug().
				Str("recipient", recipientID).
				Str("msg", event.MessageID).
				Int("devices", n).
				Msg("message delivered via ws")
		}
	})
	if err != nil {
		log.Fatal().Err(err).Msg("nats subscribe failed")
	}
	log.Info().Str("subject", "chat.deliver.>").Msg("nats delivery consumer ready")

	// Subscribe to conversation-creation events so receivers see new groups
	// pop into their chat list without polling. Same fan-out pattern as
	// delivery events: one NATS message → one frame per online recipient.
	_, err = nc.Subscribe("chat.conversation.created.>", func(msg *nats.Msg) {
		var event conversationCreatedEvent
		if err := json.Unmarshal(msg.Data, &event); err != nil {
			log.Error().Err(err).Msg("failed to unmarshal conversation.created event")
			return
		}
		payload := map[string]any{
			"conversation_id": event.ConversationID,
			"type":            event.Type,
			"name":            event.Name,
			"creator_id":      event.CreatorID,
			"member_ids":      event.MemberIDs,
			"created_at":      event.CreatedAt.Unix(),
		}
		for _, recipientID := range event.RecipientIDs {
			n := hub.Send(recipientID, ws.Envelope{
				Type:    "conversation_created",
				Payload: payload,
			})
			log.Debug().
				Str("recipient", recipientID).
				Str("conv", event.ConversationID).
				Int("devices", n).
				Msg("conversation_created delivered via ws")
		}
	})
	if err != nil {
		log.Fatal().Err(err).Msg("nats subscribe failed (conversation.created)")
	}
	log.Info().Str("subject", "chat.conversation.created.>").Msg("nats conversation.created consumer ready")

	// Reactions: chat service publishes "chat.reaction.{convId}" on toggle.
	// Fanout to every other member so their messages list updates live.
	_, err = nc.Subscribe("chat.reaction.>", func(msg *nats.Msg) {
		var event reactionEvent
		if err := json.Unmarshal(msg.Data, &event); err != nil {
			log.Error().Err(err).Msg("failed to unmarshal reaction event")
			return
		}
		payload := map[string]any{
			"conversation_id": event.ConversationID,
			"message_id":      event.MessageID,
			"actor_id":        event.ActorID,
			"emoji":           event.Emoji,
			"added":           event.Added,
			"reacted_at":      event.ReactedAt.Unix(),
		}
		for _, recipientID := range event.RecipientIDs {
			n := hub.Send(recipientID, ws.Envelope{
				Type:    "reaction",
				Payload: payload,
			})
			log.Debug().
				Str("recipient", recipientID).
				Str("msg", event.MessageID).
				Str("emoji", event.Emoji).
				Bool("added", event.Added).
				Int("devices", n).
				Msg("reaction delivered via ws")
		}
	})
	if err != nil {
		log.Fatal().Err(err).Msg("nats subscribe failed (reaction)")
	}
	log.Info().Str("subject", "chat.reaction.>").Msg("nats reaction consumer ready")

	// Edits: chat service publishes "chat.edit.{convId}" when a message's
	// ciphertext is replaced. Fanout so receivers can re-decrypt the new
	// ciphertext into their cached plaintext for the same message id.
	_, err = nc.Subscribe("chat.edit.>", func(msg *nats.Msg) {
		var event editEvent
		if err := json.Unmarshal(msg.Data, &event); err != nil {
			log.Error().Err(err).Msg("failed to unmarshal edit event")
			return
		}
		payload := map[string]any{
			"conversation_id": event.ConversationID,
			"message_id":      event.MessageID,
			"sender_id":       event.SenderID,
			"ciphertext":      event.Ciphertext,
			"edited_at":       event.EditedAt.Unix(),
		}
		for _, recipientID := range event.RecipientIDs {
			n := hub.Send(recipientID, ws.Envelope{
				Type:    "message_edited",
				Payload: payload,
			})
			log.Debug().
				Str("recipient", recipientID).
				Str("msg", event.MessageID).
				Int("devices", n).
				Msg("message_edited delivered via ws")
		}
	})
	if err != nil {
		log.Fatal().Err(err).Msg("nats subscribe failed (edit)")
	}
	log.Info().Str("subject", "chat.edit.>").Msg("nats edit consumer ready")

	// Pinned messages: chat publishes "chat.pin.{convId}" on toggle.
	_, err = nc.Subscribe("chat.pin.>", func(msg *nats.Msg) {
		var event pinEvent
		if err := json.Unmarshal(msg.Data, &event); err != nil {
			log.Error().Err(err).Msg("failed to unmarshal pin event")
			return
		}
		payload := map[string]any{
			"conversation_id": event.ConversationID,
			"message_id":      event.MessageID,
			"actor_id":        event.ActorID,
			"pinned":          event.Pinned,
			"at":              event.At.Unix(),
		}
		for _, recipientID := range event.RecipientIDs {
			n := hub.Send(recipientID, ws.Envelope{
				Type:    "message_pinned",
				Payload: payload,
			})
			log.Debug().
				Str("recipient", recipientID).
				Str("msg", event.MessageID).
				Bool("pinned", event.Pinned).
				Int("devices", n).
				Msg("message_pinned delivered via ws")
		}
	})
	if err != nil {
		log.Fatal().Err(err).Msg("nats subscribe failed (pin)")
	}
	log.Info().Str("subject", "chat.pin.>").Msg("nats pin consumer ready")

	// ─── REST API router ──────────────────────────────────────────────────────
	r := chi.NewRouter()
	r.Use(chimw.RequestID)
	r.Use(chimw.RealIP)
	r.Use(chimw.Recoverer)
	r.Use(chimw.Timeout(30 * time.Second))
	r.Use(requestLogger(log))
	r.Use(gwmw.RateLimit)

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"ok","version":"` + version + `"}`))
	})

	// Public auth routes — bootstrap a session before any token exists.
	// `register` / `restore` create accounts; the `token/*` trio handles
	// rotation; `prekeys/bundle/{id}` is a public lookup so a peer can
	// fetch your prekey to start an X3DH handshake.
	r.Route("/v1/auth", func(r chi.Router) {
		r.Post("/register", proxyTo(upstreams["auth"]))
		r.Post("/restore",  proxyTo(upstreams["auth"]))
		r.Route("/token", func(r chi.Router) {
			r.Handle("/*", upstreams["auth"])
		})
		r.Get("/prekeys/bundle/{accountID}", proxyTo(upstreams["auth"]))
	})

	// Protected routes (JWT required)
	r.Group(func(r chi.Router) {
		r.Use(gwmw.JWTAuth(tokenMgr))

		// Authenticated auth routes — needs X-Account-ID injected by JWTAuth.
		r.Route("/v1/auth/sessions", func(r chi.Router) {
			r.Handle("/*", upstreams["auth"])
			r.Handle("/", upstreams["auth"])
		})
		r.Route("/v1/auth/prekeys/publish", func(r chi.Router) {
			r.Handle("/*", upstreams["auth"])
			r.Handle("/", upstreams["auth"])
		})

		// Онлайн по активным WS-сессиям шлюза (для списка чатов / контактов).
		r.Post("/v1/presence", handlePeerPresence(hub))

		r.Route("/v1/conversations", func(r chi.Router) {
			r.Handle("/*", upstreams["chat"])
		})
		r.Route("/v1/users", func(r chi.Router) {
			r.Handle("/*", upstreams["user"])
		})
		r.Route("/v1/contacts", func(r chi.Router) {
			r.Handle("/*", upstreams["user"])
		})
		r.Route("/v1/keys", func(r chi.Router) {
			r.Handle("/*", upstreams["user"])
		})
		r.Route("/v1/media", func(r chi.Router) {
			r.Handle("/*", upstreams["media"])
		})
		r.Route("/v1/bots", func(r chi.Router) {
			r.Handle("/*", upstreams["bot"])
		})
		r.Route("/v1/devices", func(r chi.Router) {
			r.Handle("/*", upstreams["notification"])
		})
		r.Route("/v1/moderation", func(r chi.Router) {
			r.Handle("/*", upstreams["moderation"])
		})
	})

	// ─── REST server ──────────────────────────────────────────────────────────
	restAddr := env("HTTP_ADDR", ":8080")
	restSrv := &http.Server{
		Addr:         restAddr,
		Handler:      r,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	// ─── WebSocket server ─────────────────────────────────────────────────────
	wsMux := http.NewServeMux()
	wsMux.HandleFunc("/ws", wsHandler(hub, tokenMgr, log))
	wsMux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("ok"))
	})

	wsAddr := env("WS_ADDR", ":9080")
	wsSrv := &http.Server{
		Addr:        wsAddr,
		Handler:     wsMux,
		ReadTimeout: 0, // WebSocket connections are long-lived
		WriteTimeout: 0,
		IdleTimeout:  0,
	}

	go func() {
		log.Info().Str("addr", restAddr).Msg("REST gateway listening")
		if err := restSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal().Err(err).Msg("REST server error")
		}
	}()

	go func() {
		log.Info().Str("addr", wsAddr).Msg("WebSocket gateway listening")
		if err := wsSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal().Err(err).Msg("WS server error")
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Info().Msg("shutting down gateway")
	shutCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	_ = restSrv.Shutdown(shutCtx)
	_ = wsSrv.Shutdown(shutCtx)
}

// proxyTo wraps an upstream so we can register it under specific HTTP methods
// without giving up chi's route-tree behaviour.
func proxyTo(up *proxy.Upstream) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) { up.ServeHTTP(w, r) }
}

const maxPresencePeerIDs = 256

// handlePeerPresence returns whether each requested account_id currently has an
// active WebSocket on this gateway (same hub as message delivery).
func handlePeerPresence(hub *ws.Hub) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusMethodNotAllowed)
			_, _ = w.Write([]byte(`{"error":"method not allowed"}`))
			return
		}
		var body struct {
			PeerIDs []string `json:"peer_ids"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			_, _ = w.Write([]byte(`{"error":"invalid json body"}`))
			return
		}
		if len(body.PeerIDs) > maxPresencePeerIDs {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			_, _ = w.Write([]byte(`{"error":"too many peer_ids"}`))
			return
		}
		out := make(map[string]bool, len(body.PeerIDs))
		for _, raw := range body.PeerIDs {
			id := strings.TrimSpace(raw)
			if id == "" {
				continue
			}
			out[id] = hub.IsOnline(id)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_ = json.NewEncoder(w).Encode(map[string]any{"peers": out})
	}
}

// wsHandler upgrades HTTP connections to WebSocket and serves the client.
func wsHandler(hub *ws.Hub, tokenMgr *token.Manager, log zerolog.Logger) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// Auth via ?token= query param (headers become inaccessible after WS upgrade).
		accessToken := r.URL.Query().Get("token")
		if accessToken == "" {
			http.Error(w, "missing token", http.StatusUnauthorized)
			return
		}

		claims, err := tokenMgr.Verify(accessToken)
		if err != nil {
			http.Error(w, "invalid token", http.StatusUnauthorized)
			return
		}

		allowedOrigin := env("ALLOWED_ORIGIN", "")
		originPatterns := []string{"localhost:*"}
		if allowedOrigin != "" {
			originPatterns = []string{allowedOrigin, "localhost:*"}
		}
		conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
			OriginPatterns: originPatterns,
		})
		if err != nil {
			log.Error().Err(err).Msg("ws accept failed")
			return
		}

		client := ws.NewClient(claims.AccountID, conn)
		log.Debug().Str("account", claims.AccountID).Msg("ws client connected")
		hub.ServeClient(r.Context(), client)
		log.Debug().Str("account", claims.AccountID).Msg("ws client disconnected")
	}
}

func requestLogger(log zerolog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			log.Debug().
				Str("method", r.Method).
				Str("path", r.URL.Path).
				Str("ip", r.RemoteAddr).
				Msg("request")
			next.ServeHTTP(w, r)
		})
	}
}

func mustEnv(key string) string {
	v := os.Getenv(key)
	if v == "" {
		panic("required env " + key + " is not set")
	}
	return v
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
