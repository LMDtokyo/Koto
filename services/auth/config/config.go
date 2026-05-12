// Package config loads auth service configuration from environment variables.
package config

import (
	"fmt"
	"os"
	"time"
)

// Config holds all configuration for the auth service.
type Config struct {
	HTTPAddr          string
	PostgresDSN       string
	DragonflyAddr     string
	DragonflyPassword string
	JWTPrivateKeySeed string
	JWTAccessTTL      time.Duration
	JWTRefreshTTL     time.Duration
	LogLevel          string
}

// Load reads configuration from environment variables.
func Load() (Config, error) {
	accessTTL, err := parseDuration(env("JWT_ACCESS_TTL", "15m"))
	if err != nil {
		return Config{}, fmt.Errorf("JWT_ACCESS_TTL: %w", err)
	}
	refreshTTL, err := parseDuration(env("JWT_REFRESH_TTL", "720h"))
	if err != nil {
		return Config{}, fmt.Errorf("JWT_REFRESH_TTL: %w", err)
	}

	seed := os.Getenv("JWT_PRIVATE_KEY_SEED")
	if seed == "" {
		return Config{}, fmt.Errorf("JWT_PRIVATE_KEY_SEED is required")
	}

	return Config{
		HTTPAddr:          env("HTTP_ADDR", ":18001"),
		PostgresDSN:       mustEnv("POSTGRES_DSN"),
		DragonflyAddr:     env("DRAGONFLY_ADDR", "localhost:6379"),
		DragonflyPassword: os.Getenv("DRAGONFLY_PASSWORD"),
		JWTPrivateKeySeed: seed,
		JWTAccessTTL:      accessTTL,
		JWTRefreshTTL:     refreshTTL,
		LogLevel:          env("LOG_LEVEL", "info"),
	}, nil
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
		panic(fmt.Sprintf("required env %s is not set", key))
	}
	return v
}

func parseDuration(s string) (time.Duration, error) {
	return time.ParseDuration(s)
}
