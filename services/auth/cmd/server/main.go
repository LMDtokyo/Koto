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
	"github.com/koto-messenger/koto/pkg/token"
	"github.com/koto-messenger/koto/services/auth/config"
	"github.com/koto-messenger/koto/services/auth/internal/app"
	"github.com/koto-messenger/koto/services/auth/internal/infra/cache"
	"github.com/koto-messenger/koto/services/auth/internal/infra/postgres"
	httptransport "github.com/koto-messenger/koto/services/auth/internal/transport/http"
)

var version = "dev"

func main() {
	cfg, err := config.Load()
	if err != nil {
		os.Stderr.WriteString("config: " + err.Error() + "\n")
		os.Exit(1)
	}

	log := logger.New("auth", version, cfg.LogLevel == "debug")

	// ─── Infrastructure ───────────────────────────────────────────────────────
	ctx := context.Background()

	pool, err := postgres.NewPool(ctx, cfg.PostgresDSN)
	if err != nil {
		log.Fatal().Err(err).Msg("postgres connect failed")
	}
	defer pool.Close()
	log.Info().Msg("postgres connected")

	rdb := cache.NewRedisClient(cfg.DragonflyAddr, cfg.DragonflyPassword)
	defer rdb.Close()
	if err := cache.Ping(ctx, rdb); err != nil {
		log.Fatal().Err(err).Msg("dragonfly connect failed")
	}
	log.Info().Msg("dragonfly connected")

	// ─── Domain repositories ─────────────────────────────────────────────────
	accountRepo := postgres.NewAccountRepo(pool)
	preKeyRepo  := postgres.NewPreKeyRepo(pool)
	refreshRepo := cache.NewRefreshTokenCache(rdb)
	sessionRepo := postgres.NewSessionRepo(pool)

	// ─── Token manager ────────────────────────────────────────────────────────
	tokenMgr, err := token.NewManager(cfg.JWTPrivateKeySeed, cfg.JWTAccessTTL)
	if err != nil {
		log.Fatal().Err(err).Msg("token manager init failed")
	}

	// ─── Application service ─────────────────────────────────────────────────
	svc := app.New(accountRepo, preKeyRepo, refreshRepo, sessionRepo, tokenMgr, cfg.JWTRefreshTTL)

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
		log.Info().Str("addr", cfg.HTTPAddr).Msg("auth service listening")
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal().Err(err).Msg("server error")
		}
	}()

	// ─── Graceful shutdown ────────────────────────────────────────────────────
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Info().Msg("shutting down auth service")
	shutCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutCtx); err != nil {
		log.Error().Err(err).Msg("forced shutdown")
	}
}
