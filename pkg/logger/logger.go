// Package logger provides a structured logger built on zerolog.
package logger

import (
	"context"
	"io"
	"os"

	"github.com/rs/zerolog"
)

// New creates a service-level structured logger.
// service should be the service name (e.g. "auth", "chat").
// version is the build version string injected at compile time.
// debug enables DEBUG-level output; otherwise INFO and above.
func New(service, version string, debug bool) zerolog.Logger {
	zerolog.TimeFieldFormat = zerolog.TimeFormatUnixMs

	level := zerolog.InfoLevel
	if debug {
		level = zerolog.DebugLevel
	}

	return newLogger(os.Stderr, level, service, version)
}

func newLogger(w io.Writer, level zerolog.Level, service, version string) zerolog.Logger {
	return zerolog.New(w).
		Level(level).
		With().
		Timestamp().
		Str("service", service).
		Str("version", version).
		Logger()
}

// WithCtx attaches logger l to ctx and returns the updated context.
func WithCtx(ctx context.Context, l zerolog.Logger) context.Context {
	return l.WithContext(ctx)
}

// FromCtx retrieves the zerolog.Logger stored in ctx.
// If no logger is present it returns the global logger.
func FromCtx(ctx context.Context) *zerolog.Logger {
	return zerolog.Ctx(ctx)
}
