package main

import (
	"context"
	"errors"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/koto-messenger/koto/pkg/logger"
	"github.com/koto-messenger/koto/services/chat/config"
	"github.com/koto-messenger/koto/services/chat/internal/app"
	"github.com/koto-messenger/koto/services/chat/internal/infra/broker"
	"github.com/koto-messenger/koto/services/chat/internal/infra/scylla"
	httptransport "github.com/koto-messenger/koto/services/chat/internal/transport/http"
)

var version = "dev"

func main() {
	cfg, err := config.Load()
	if err != nil {
		os.Stderr.WriteString("config: " + err.Error() + "\n")
		os.Exit(1)
	}

	log := logger.New("chat", version, cfg.LogLevel == "debug")

	// ─── ScyllaDB ─────────────────────────────────────────────────────────────
	session, err := scylla.NewSession(cfg.ScyllaHosts, cfg.ScyllaKeyspace)
	if err != nil {
		log.Fatal().Err(err).Msg("scylladb connect failed")
	}
	defer session.Close()
	log.Info().Strs("hosts", cfg.ScyllaHosts).Msg("scylladb connected")

	// ─── NATS JetStream ───────────────────────────────────────────────────────
	publisher, cleanupNATS, err := broker.NewNATSPublisher(cfg.NatsURL)
	if err != nil {
		log.Fatal().Err(err).Msg("nats connect failed")
	}
	defer cleanupNATS()
	log.Info().Str("url", cfg.NatsURL).Msg("nats connected")

	// ─── Repositories ─────────────────────────────────────────────────────────
	messageRepo  := scylla.NewMessageRepo(session)
	convRepo     := scylla.NewConversationRepo(session)
	reactionRepo := scylla.NewReactionRepo(session)

	// ─── Application service ──────────────────────────────────────────────────
	svc := app.New(messageRepo, convRepo, reactionRepo, publisher)

	// ─── HTTP server ──────────────────────────────────────────────────────────
	handler := httptransport.NewHandler(svc, log)
	srv := &http.Server{
		Addr:         cfg.HTTPAddr,
		Handler:      handler.Router(),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	go func() {
		log.Info().Str("addr", cfg.HTTPAddr).Msg("chat service listening")
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal().Err(err).Msg("server error")
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Info().Msg("shutting down chat service")
	shutCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutCtx); err != nil {
		log.Error().Err(err).Msg("forced shutdown")
	}
}
