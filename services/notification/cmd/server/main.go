package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/nats-io/nats.go"
	"github.com/nats-io/nats.go/jetstream"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/pkg/logger"
	"github.com/koto-messenger/koto/services/notification/internal/domain"
	"github.com/koto-messenger/koto/services/notification/internal/infra/apns"
	"github.com/koto-messenger/koto/services/notification/internal/infra/postgres"
	"github.com/koto-messenger/koto/services/notification/internal/infra/unified_push"
)

var version = "dev"

// DeliveryEvent mirrors chat.DeliveryEvent (imported inline to avoid circular deps).
type DeliveryEvent struct {
	MessageID      string   `json:"msg_id"`
	ConversationID string   `json:"conv_id"`
	SenderID       string   `json:"sender_id"`
	RecipientIDs   []string `json:"recipient_ids"`
}

func main() {
	log := logger.New("notification", version, os.Getenv("LOG_LEVEL") == "debug")

	ctx := context.Background()

	// ─── Postgres ─────────────────────────────────────────────────────────────
	pool, err := pgxpool.New(ctx, mustEnv("POSTGRES_DSN"))
	if err != nil {
		log.Fatal().Err(err).Msg("postgres connect failed")
	}
	defer pool.Close()

	// ─── APNs ─────────────────────────────────────────────────────────────────
	var apnsClient *apns.Client
	keyPath := os.Getenv("APNS_KEY_PATH")
	if keyPath != "" {
		apnsClient, err = apns.NewClient(
			keyPath,
			mustEnv("APNS_KEY_ID"),
			mustEnv("APNS_TEAM_ID"),
			mustEnv("APNS_BUNDLE_ID"),
			os.Getenv("APNS_PRODUCTION") == "true",
		)
		if err != nil {
			log.Warn().Err(err).Msg("APNs disabled — key load failed")
		} else {
			log.Info().Msg("APNs enabled")
		}
	}

	upClient := unified_push.New()

	// ─── NATS JetStream consumer ──────────────────────────────────────────────
	nc, err := nats.Connect(mustEnv("NATS_URL"),
		nats.RetryOnFailedConnect(true),
		nats.MaxReconnects(-1),
	)
	if err != nil {
		log.Fatal().Err(err).Msg("nats connect failed")
	}
	defer nc.Drain()

	js, err := jetstream.New(nc)
	if err != nil {
		log.Fatal().Err(err).Msg("jetstream init failed")
	}

	stream, err := js.CreateOrUpdateStream(ctx, jetstream.StreamConfig{
		Name:     "CHAT",
		Subjects: []string{"chat.>"},
		Storage:  jetstream.FileStorage,
	})
	if err != nil {
		log.Fatal().Err(err).Msg("chat stream init failed")
	}

	cons, err := stream.CreateOrUpdateConsumer(ctx, jetstream.ConsumerConfig{
		Durable:       "notification-worker",
		AckPolicy:     jetstream.AckExplicitPolicy,
		FilterSubject: "chat.deliver.>",
		MaxDeliver:    5,
		AckWait:       30 * time.Second,
	})
	if err != nil {
		log.Fatal().Err(err).Msg("create consumer failed")
	}

	// ─── Consume loop ─────────────────────────────────────────────────────────
	consumeCtx, err := cons.Consume(func(msg jetstream.Msg) {
		var event DeliveryEvent
		if err := json.Unmarshal(msg.Data(), &event); err != nil {
			log.Error().Err(err).Msg("unmarshal delivery event")
			_ = msg.Nak()
			return
		}

		for _, recipientID := range event.RecipientIDs {
			devices, err := listDevices(ctx, pool, recipientID)
			if err != nil {
				log.Error().Err(err).Str("account", recipientID).Msg("list devices")
				continue
			}

			for _, d := range devices {
				switch d.Platform {
				case domain.PlatformIOS:
					if apnsClient != nil {
						if err := apnsClient.SendSilent(d.Token, event.ConversationID, 1); err != nil {
							log.Warn().Err(err).Str("token", d.Token[:8]+"…").Msg("apns failed")
						}
					}
				case domain.PlatformAndroid:
					if err := upClient.Notify(ctx, d.Token, event.ConversationID); err != nil {
						log.Warn().Err(err).Msg("unified_push failed")
					}
				}
			}
		}

		_ = msg.Ack()
	})
	if err != nil {
		log.Fatal().Err(err).Msg("start consumer failed")
	}
	defer consumeCtx.Stop()

	// ─── HTTP API (device registration) ──────────────────────────────────────
	repo := postgres.NewDeviceRepo(pool)

	router := chi.NewRouter()
	router.Route("/v1/devices", func(r chi.Router) {
		r.Put("/", func(w http.ResponseWriter, r *http.Request) {
			var req struct {
				Token    string `json:"token"`    // UnifiedPush endpoint URL
				Platform int    `json:"platform"` // 2=Android
			}
			if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
				http.Error(w, "invalid body", 400)
				return
			}
			accountID := r.Header.Get("X-Account-ID") // set by gateway auth middleware
			if accountID == "" {
				http.Error(w, "unauthorized", 401)
				return
			}
			err := repo.Upsert(r.Context(), domain.Device{
				AccountID: accountID,
				Token:     req.Token,
				Platform:  domain.Platform(req.Platform),
				BundleID:  "run.koto",
			})
			if err != nil {
				log.Error().Err(err).Msg("upsert device")
				http.Error(w, "internal error", 500)
				return
			}
			w.WriteHeader(http.StatusNoContent)
		})
		r.Delete("/", func(w http.ResponseWriter, r *http.Request) {
			var req struct {
				Token string `json:"token"`
			}
			if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
				http.Error(w, "invalid body", 400)
				return
			}
			_ = repo.RemoveToken(r.Context(), req.Token)
			w.WriteHeader(http.StatusNoContent)
		})
	})

	httpPort := os.Getenv("HTTP_PORT")
	if httpPort == "" {
		httpPort = "18004"
	}
	go func() {
		log.Info().Str("port", httpPort).Msg("notification HTTP server started")
		if err := http.ListenAndServe(":"+httpPort, router); err != nil {
			log.Fatal().Err(err).Msg("http server failed")
		}
	}()

	log.Info().Msg("notification service running")

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Info().Msg("shutting down notification service")
}

func listDevices(ctx context.Context, pool *pgxpool.Pool, accountID string) ([]domain.Device, error) {
	rows, err := pool.Query(ctx,
		`SELECT id, account_id, token, platform, bundle_id FROM devices WHERE account_id = $1`,
		accountID,
	)
	if err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "list devices", err)
	}
	defer rows.Close()

	var devices []domain.Device
	for rows.Next() {
		var d domain.Device
		var platform uint8
		if err := rows.Scan(&d.ID, &d.AccountID, &d.Token, &platform, &d.BundleID); err != nil {
			return nil, err
		}
		d.Platform = domain.Platform(platform)
		devices = append(devices, d)
	}
	return devices, rows.Err()
}

func mustEnv(key string) string {
	v := os.Getenv(key)
	if v == "" {
		panic(fmt.Sprintf("required env %s not set", key))
	}
	return v
}
