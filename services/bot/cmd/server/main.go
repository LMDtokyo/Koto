package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/pkg/logger"
	"github.com/koto-messenger/koto/services/bot/internal/domain"
)

var version = "dev"

func main() {
	log := logger.New("bot", version, os.Getenv("LOG_LEVEL") == "debug")
	ctx := context.Background()

	pool, err := pgxpool.New(ctx, mustEnv("POSTGRES_DSN"))
	if err != nil {
		log.Fatal().Err(err).Msg("postgres connect failed")
	}
	defer pool.Close()

	client := &http.Client{Timeout: 10 * time.Second}

	r := chi.NewRouter()
	r.Use(middleware.RequestID, middleware.RealIP, middleware.Recoverer)

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]string{"status": "ok"})
	})

	// ─── Bot Registration API ─────────────────────────────────────────────────
	r.Route("/v1/bots", func(r chi.Router) {
		r.Use(accountIDMiddleware)

		r.Post("/", func(w http.ResponseWriter, r *http.Request) {
			ownerID := accountID(r)
			var body struct {
				Name       string `json:"name"`
				Username   string `json:"username"`
				WebhookURL string `json:"webhook_url"`
			}
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				writeError(w, 400, "invalid body")
				return
			}
			if body.Username == "" || body.Name == "" {
				writeError(w, 400, "name and username required")
				return
			}

			botToken, err := generateToken()
			if err != nil {
				writeError(w, 500, "token generation failed")
				return
			}
			bot := domain.Bot{
				ID:         generateID(),
				AccountID:  generateID(), // bot gets its own messenger account
				OwnerID:    ownerID,
				Name:       body.Name,
				Username:   body.Username,
				Token:      botToken,
				WebhookURL: body.WebhookURL,
				Active:     true,
				CreatedAt:  time.Now().UTC(),
			}

			_, err = pool.Exec(ctx,
				`INSERT INTO bots (id, account_id, owner_id, name, username, token, webhook_url, active, created_at)
				 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
				bot.ID, bot.AccountID, bot.OwnerID, bot.Name, bot.Username,
				bot.Token, bot.WebhookURL, bot.Active, bot.CreatedAt,
			)
			if err != nil {
				if isUniqueViolation(err) {
					writeError(w, 409, "username already taken")
					return
				}
				writeError(w, 500, "internal error")
				return
			}

			writeJSON(w, 201, map[string]any{
				"id":       bot.ID,
				"token":    bot.Token, // return once on creation
				"username": bot.Username,
			})
		})

		r.Get("/", func(w http.ResponseWriter, r *http.Request) {
			ownerID := accountID(r)
			rows, err := pool.Query(ctx,
				`SELECT id, account_id, name, username, webhook_url, active, created_at
				 FROM bots WHERE owner_id = $1 ORDER BY created_at DESC`, ownerID,
			)
			if err != nil {
				writeError(w, 500, "internal error")
				return
			}
			defer rows.Close()

			var bots []domain.Bot
			for rows.Next() {
				var b domain.Bot
				_ = rows.Scan(&b.ID, &b.AccountID, &b.Name, &b.Username, &b.WebhookURL, &b.Active, &b.CreatedAt)
				bots = append(bots, b)
			}
			writeJSON(w, 200, bots)
		})

		r.Delete("/{botID}", func(w http.ResponseWriter, r *http.Request) {
			botID := chi.URLParam(r, "botID")
			_, err := pool.Exec(ctx, `DELETE FROM bots WHERE id = $1 AND owner_id = $2`, botID, accountID(r))
			if err != nil {
				writeError(w, 500, "internal error")
				return
			}
			w.WriteHeader(204)
		})
	})

	// ─── Internal: dispatch event to bot webhook (called by gateway/chat) ────
	// Protected by INTERNAL_SECRET shared between services — not exposed externally.
	internalSecret := mustEnv("INTERNAL_SECRET")
	r.Post("/internal/dispatch", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Internal-Secret") != internalSecret {
			writeError(w, 403, "forbidden")
			return
		}

		var event domain.BotEvent
		if err := json.NewDecoder(r.Body).Decode(&event); err != nil {
			writeError(w, 400, "invalid body")
			return
		}

		var webhookURL string
		err := pool.QueryRow(ctx,
			`SELECT webhook_url FROM bots WHERE account_id = $1 AND active = true`, event.BotID,
		).Scan(&webhookURL)
		if err != nil || webhookURL == "" {
			w.WriteHeader(204) // bot not found or no webhook — ignore
			return
		}

		payload, _ := json.Marshal(event)
		// Use a dedicated timeout independent of the incoming request context
		webhookCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		req, err := http.NewRequestWithContext(webhookCtx, http.MethodPost, webhookURL, bytes.NewReader(payload))
		if err != nil {
			log.Warn().Err(err).Msg("build webhook request failed")
			w.WriteHeader(204)
			return
		}
		req.Header.Set("Content-Type", "application/json")
		resp, err := client.Do(req)
		if err != nil {
			log.Warn().Err(err).Str("webhook", webhookURL).Msg("webhook delivery failed")
		} else {
			// Always drain and close the body to avoid connection leaks
			_, _ = io.Copy(io.Discard, resp.Body)
			resp.Body.Close()
		}
		w.WriteHeader(204)
	})

	addr := env("HTTP_ADDR", ":18006")
	srv := &http.Server{
		Addr:         addr,
		Handler:      r,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	go func() {
		log.Info().Str("addr", addr).Msg("bot service listening")
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal().Err(err).Msg("server error")
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	shutCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutCtx)
}

type ctxKey string

const accountIDCtxKey ctxKey = "account_id"

func accountIDMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := r.Header.Get("X-Account-ID")
		if id == "" {
			writeError(w, 401, "X-Account-ID required")
			return
		}
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), accountIDCtxKey, id)))
	})
}

func accountID(r *http.Request) string {
	id, _ := r.Context().Value(accountIDCtxKey).(string)
	return id
}

func generateID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func generateToken() (string, error) {
	b := make([]byte, 32)
	_, err := rand.Read(b)
	return hex.EncodeToString(b), err
}

func isUniqueViolation(err error) bool {
	var pgErr interface{ SQLState() string }
	if errors.As(err, &pgErr) {
		return pgErr.SQLState() == "23505"
	}
	return false
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
func mustEnv(key string) string {
	v := os.Getenv(key)
	if v == "" { panic(fmt.Sprintf("required env %s not set", key)) }
	return v
}
func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" { return v }
	return fallback
}
