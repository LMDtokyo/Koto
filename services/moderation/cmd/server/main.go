// Service: koto-moderation. Endpoint /v1/moderation/* за gateway'ем.
package main

import (
	"context"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog/log"

	"github.com/koto-messenger/koto/services/moderation/internal/app"
	"github.com/koto-messenger/koto/services/moderation/internal/infra"
	"github.com/koto-messenger/koto/services/moderation/internal/infra/postgres"
	httpx "github.com/koto-messenger/koto/services/moderation/internal/transport/http"
)

func main() {
	dsn := mustEnv("POSTGRES_DSN")
	httpAddr := env("HTTP_ADDR", ":18007")

	pool, err := pgxpool.New(context.Background(), dsn)
	if err != nil {
		log.Fatal().Err(err).Msg("postgres connect")
	}
	defer pool.Close()

	reportRepo := postgres.NewReportRepo(pool)
	actionRepo := postgres.NewActionRepo(pool)
	classifier := infra.NewOpenAIClassifier()

	svc := app.New(reportRepo, actionRepo, classifier)
	handler := httpx.New(svc)

	srv := &http.Server{
		Addr:         httpAddr,
		Handler:      handler.Router(),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
	}

	go func() {
		log.Info().Str("addr", httpAddr).Msg("moderation: listening")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal().Err(err).Msg("http listen")
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop
	log.Info().Msg("moderation: shutting down")
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = srv.Shutdown(ctx)
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func mustEnv(key string) string {
	v := os.Getenv(key)
	if v == "" {
		log.Fatal().Str("key", key).Msg("missing required env var")
	}
	return v
}
