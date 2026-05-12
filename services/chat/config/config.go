package config

import (
	"fmt"
	"os"
	"strings"
)

type Config struct {
	HTTPAddr          string
	ScyllaHosts       []string
	ScyllaKeyspace    string
	NatsURL           string
	DragonflyAddr     string
	DragonflyPassword string
	JWTPublicKey      string
	LogLevel          string
}

func Load() (Config, error) {
	scyllaHostsRaw := env("SCYLLA_HOSTS", "localhost:9042")
	hosts := strings.Split(scyllaHostsRaw, ",")
	for i := range hosts {
		hosts[i] = strings.TrimSpace(hosts[i])
	}

	pubKey := os.Getenv("JWT_PUBLIC_KEY")
	if pubKey == "" {
		return Config{}, fmt.Errorf("JWT_PUBLIC_KEY is required")
	}

	return Config{
		HTTPAddr:          env("HTTP_ADDR", ":18002"),
		ScyllaHosts:       hosts,
		ScyllaKeyspace:    env("SCYLLA_KEYSPACE", "koto"),
		NatsURL:           env("NATS_URL", "nats://localhost:4222"),
		DragonflyAddr:     env("DRAGONFLY_ADDR", "localhost:6379"),
		DragonflyPassword: os.Getenv("DRAGONFLY_PASSWORD"),
		JWTPublicKey:      pubKey,
		LogLevel:          env("LOG_LEVEL", "info"),
	}, nil
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
