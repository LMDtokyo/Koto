package main

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/pkg/logger"
	"github.com/koto-messenger/koto/services/user/internal/app"
	"github.com/koto-messenger/koto/services/user/internal/domain"
	"github.com/koto-messenger/koto/services/user/internal/infra/postgres"
)

var version = "dev"

func main() {
	log := logger.New("user", version, os.Getenv("LOG_LEVEL") == "debug")

	ctx := context.Background()
	pool, err := postgres.NewPool(ctx, mustEnv("POSTGRES_DSN"))
	if err != nil {
		log.Fatal().Err(err).Msg("postgres connect failed")
	}
	defer pool.Close()

	svc := app.New(postgres.NewProfileRepo(pool), postgres.NewContactRepo(pool), postgres.NewPrekeyRepo(pool))

	// ─── Inline router (small service — no separate transport package) ────────
	r := chi.NewRouter()
	r.Use(middleware.RequestID, middleware.RealIP, middleware.Recoverer)

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]string{"status": "ok"})
	})

	r.Route("/v1/users", func(r chi.Router) {
		r.Use(accountIDMiddleware)

		r.Get("/me", func(w http.ResponseWriter, r *http.Request) {
			id := accountID(r)
			p, err := svc.GetProfile(r.Context(), id)
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, p)
		})

		r.Put("/me", func(w http.ResponseWriter, r *http.Request) {
			id := accountID(r)
			var body struct {
				DisplayName string `json:"display_name"`
				AvatarURL   string `json:"avatar_url"`
				BannerURL   string `json:"banner_url"`
				Bio         string `json:"bio"`
			}
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				writeError(w, 400, "invalid body")
				return
			}
			p, err := svc.UpdateProfile(r.Context(), app.UpdateProfileInput{
				AccountID:   id,
				DisplayName: body.DisplayName,
				AvatarURL:   body.AvatarURL,
				BannerURL:   body.BannerURL,
				Bio:         body.Bio,
			})
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, p)
		})

		r.Get("/by-username/{username}", func(w http.ResponseWriter, r *http.Request) {
			p, err := svc.FindByUsername(r.Context(), chi.URLParam(r, "username"))
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, p)
		})

		r.Get("/search", func(w http.ResponseWriter, r *http.Request) {
			res, err := svc.SearchProfiles(r.Context(), app.SearchProfilesInput{
				Query:  r.URL.Query().Get("q"),
				Limit:  atoiDefault(r.URL.Query().Get("limit"), 20),
				Cursor: r.URL.Query().Get("cursor"),
			})
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, map[string]any{
				"items":       res.Profiles,
				"next_cursor": res.NextCursor,
				"has_more":    res.HasMore,
			})
		})

		r.Get("/username-available/{username}", func(w http.ResponseWriter, r *http.Request) {
			res, err := svc.CheckUsername(r.Context(), chi.URLParam(r, "username"), accountID(r))
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, res)
		})

		r.Put("/me/username", func(w http.ResponseWriter, r *http.Request) {
			var body struct {
				Username string `json:"username"`
			}
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				writeError(w, 400, "invalid body")
				return
			}
			saved, err := svc.SetUsername(r.Context(), accountID(r), body.Username)
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, map[string]string{"username": saved})
		})

		r.Get("/{accountID}", func(w http.ResponseWriter, r *http.Request) {
			p, err := svc.GetProfile(r.Context(), chi.URLParam(r, "accountID"))
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, p)
		})

		r.Post("/batch", func(w http.ResponseWriter, r *http.Request) {
			var body struct{ IDs []string `json:"ids"` }
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				writeError(w, 400, "invalid body")
				return
			}
			ps, err := svc.GetProfiles(r.Context(), body.IDs)
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, ps)
		})
	})

	r.Route("/v1/contacts", func(r chi.Router) {
		r.Use(accountIDMiddleware)

		r.Get("/", func(w http.ResponseWriter, r *http.Request) {
			cs, err := svc.ListContacts(r.Context(), accountID(r))
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, cs)
		})

		r.Post("/", func(w http.ResponseWriter, r *http.Request) {
			var body struct {
				ContactID string `json:"contact_id"`
				Nickname  string `json:"nickname"`
			}
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				writeError(w, 400, "invalid body")
				return
			}
			if err := svc.AddContact(r.Context(), accountID(r), body.ContactID, body.Nickname); err != nil {
				writeAppError(w, err)
				return
			}
			w.WriteHeader(204)
		})

		r.Delete("/{contactID}", func(w http.ResponseWriter, r *http.Request) {
			if err := svc.RemoveContact(r.Context(), accountID(r), chi.URLParam(r, "contactID")); err != nil {
				writeAppError(w, err)
				return
			}
			w.WriteHeader(204)
		})

		r.Post("/{contactID}/block", func(w http.ResponseWriter, r *http.Request) {
			if err := svc.BlockContact(r.Context(), accountID(r), chi.URLParam(r, "contactID")); err != nil {
				writeAppError(w, err)
				return
			}
			w.WriteHeader(204)
		})

		r.Delete("/{contactID}/block", func(w http.ResponseWriter, r *http.Request) {
			if err := svc.UnblockContact(r.Context(), accountID(r), chi.URLParam(r, "contactID")); err != nil {
				writeAppError(w, err)
				return
			}
			w.WriteHeader(204)
		})

		r.Post("/requests", func(w http.ResponseWriter, r *http.Request) {
			var body struct {
				PeerID string `json:"peer_id"`
			}
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				writeError(w, 400, "invalid body")
				return
			}
			if err := svc.SendFriendRequest(r.Context(), accountID(r), body.PeerID); err != nil {
				writeAppError(w, err)
				return
			}
			w.WriteHeader(204)
		})

		r.Get("/requests/incoming", func(w http.ResponseWriter, r *http.Request) {
			items, err := svc.ListIncomingFriendRequests(r.Context(), accountID(r))
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, items)
		})

		r.Get("/friends/overview", func(w http.ResponseWriter, r *http.Request) {
			ov, err := svc.FriendsOverview(r.Context(), accountID(r))
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, ov)
		})

		r.Post("/requests/{fromID}/accept", func(w http.ResponseWriter, r *http.Request) {
			if err := svc.AcceptFriendRequest(r.Context(), accountID(r), chi.URLParam(r, "fromID")); err != nil {
				writeAppError(w, err)
				return
			}
			w.WriteHeader(204)
		})

		r.Post("/requests/{fromID}/reject", func(w http.ResponseWriter, r *http.Request) {
			if err := svc.RejectFriendRequest(r.Context(), accountID(r), chi.URLParam(r, "fromID")); err != nil {
				writeAppError(w, err)
				return
			}
			w.WriteHeader(204)
		})

		r.Get("/requests/with/{peerID}", func(w http.ResponseWriter, r *http.Request) {
			state, err := svc.FriendRelation(r.Context(), accountID(r), chi.URLParam(r, "peerID"))
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, map[string]string{"state": state})
		})

		r.Get("/can-message/{peerID}", func(w http.ResponseWriter, r *http.Request) {
			ok, err := svc.CanMessage(r.Context(), accountID(r), chi.URLParam(r, "peerID"))
			if err != nil {
				writeAppError(w, err)
				return
			}
			writeJSON(w, 200, map[string]bool{"can_message": ok})
		})
	})

	// ── Signal Protocol PreKey API ────────────────────────────────────────────
	// PUT /v1/keys        — upload / refresh key bundle after registration
	// GET /v1/keys/{id}  — fetch a peer's bundle to initiate a session
	r.Route("/v1/keys", func(r chi.Router) {
		r.Use(accountIDMiddleware)

		// Upload own key bundle (called once at registration, re-called on key rotation).
		r.Put("/", func(w http.ResponseWriter, r *http.Request) {
			var body struct {
				IdentityKey    string `json:"identity_key"`
				RegistrationID uint32 `json:"registration_id"`
				SignedPrekey   struct {
					ID        uint32 `json:"id"`
					PublicKey string `json:"public_key"`
					Signature string `json:"signature"`
				} `json:"signed_prekey"`
				KyberPrekey struct {
					ID        uint32 `json:"id"`
					PublicKey string `json:"public_key"`
					Signature string `json:"signature"`
				} `json:"kyber_prekey"`
				OneTimePrekeys []struct {
					ID        uint32 `json:"id"`
					PublicKey string `json:"public_key"`
				} `json:"one_time_prekeys"`
			}
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				writeError(w, 400, "invalid body")
				return
			}

			ik, err := base64.StdEncoding.DecodeString(body.IdentityKey)
			if err != nil {
				writeError(w, 400, "invalid identity_key encoding")
				return
			}
			spkPub, err := base64.StdEncoding.DecodeString(body.SignedPrekey.PublicKey)
			if err != nil {
				writeError(w, 400, "invalid signed_prekey.public_key encoding")
				return
			}
			spkSig, err := base64.StdEncoding.DecodeString(body.SignedPrekey.Signature)
			if err != nil {
				writeError(w, 400, "invalid signed_prekey.signature encoding")
				return
			}
			kpkPub, err := base64.StdEncoding.DecodeString(body.KyberPrekey.PublicKey)
			if err != nil {
				writeError(w, 400, "invalid kyber_prekey.public_key encoding")
				return
			}
			kpkSig, err := base64.StdEncoding.DecodeString(body.KyberPrekey.Signature)
			if err != nil {
				writeError(w, 400, "invalid kyber_prekey.signature encoding")
				return
			}

			otpks := make([]domain.OneTimePrekey, 0, len(body.OneTimePrekeys))
			for _, k := range body.OneTimePrekeys {
				pub, err := base64.StdEncoding.DecodeString(k.PublicKey)
				if err != nil {
					writeError(w, 400, "invalid one_time_prekeys entry encoding")
					return
				}
				otpks = append(otpks, domain.OneTimePrekey{
					AccountID: accountID(r),
					PrekeyID:  k.ID,
					PublicKey: pub,
				})
			}

			if err := svc.UploadPrekeys(r.Context(), app.UploadPrekeyInput{
				AccountID:       accountID(r),
				RegistrationID:  body.RegistrationID,
				IdentityKey:     ik,
				SignedPrekeyID:  body.SignedPrekey.ID,
				SignedPrekeyPub: spkPub,
				SignedPrekeySig: spkSig,
				KyberPrekeyID:   body.KyberPrekey.ID,
				KyberPrekeyPub:  kpkPub,
				KyberPrekeySig:  kpkSig,
				OneTimePrekeys:  otpks,
			}); err != nil {
				writeAppError(w, err)
				return
			}
			w.WriteHeader(204)
		})

		// Fetch another account's key bundle to establish a session.
		r.Get("/{targetID}", func(w http.ResponseWriter, r *http.Request) {
			bundle, otpk, err := svc.FetchPrekeyBundle(r.Context(), chi.URLParam(r, "targetID"))
			if err != nil {
				writeAppError(w, err)
				return
			}

			resp := map[string]any{
				"identity_key":     base64.StdEncoding.EncodeToString(bundle.IdentityKey),
				"registration_id":  bundle.RegistrationID,
				"device_id":        1,
				"signed_prekey": map[string]any{
					"id":         bundle.SignedPrekeyID,
					"public_key": base64.StdEncoding.EncodeToString(bundle.SignedPrekeyPub),
					"signature":  base64.StdEncoding.EncodeToString(bundle.SignedPrekeySig),
				},
				"kyber_prekey": map[string]any{
					"id":         bundle.KyberPrekeyID,
					"public_key": base64.StdEncoding.EncodeToString(bundle.KyberPrekeyPub),
					"signature":  base64.StdEncoding.EncodeToString(bundle.KyberPrekeySig),
				},
			}
			if otpk != nil {
				resp["one_time_prekey"] = map[string]any{
					"id":         otpk.PrekeyID,
					"public_key": base64.StdEncoding.EncodeToString(otpk.PublicKey),
				}
			}
			writeJSON(w, 200, resp)
		})
	})

	addr := env("HTTP_ADDR", ":18003")
	srv := &http.Server{
		Addr:         addr,
		Handler:      r,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	go func() {
		log.Info().Str("addr", addr).Msg("user service listening")
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

func atoiDefault(s string, fallback int) int {
	if s == "" {
		return fallback
	}
	n, err := strconv.Atoi(s)
	if err != nil {
		return fallback
	}
	return n
}
