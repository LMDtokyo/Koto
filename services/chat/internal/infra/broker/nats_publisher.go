// Package broker implements EventPublisher backed by NATS JetStream.
package broker

import (
	"context"
	"fmt"

	"github.com/nats-io/nats.go"
	"github.com/nats-io/nats.go/jetstream"
)

// NATSPublisher implements app.EventPublisher via NATS JetStream.
type NATSPublisher struct {
	js jetstream.JetStream
}

// NewNATSPublisher connects to NATS and ensures the required stream exists.
func NewNATSPublisher(url string) (*NATSPublisher, func(), error) {
	nc, err := nats.Connect(url,
		nats.RetryOnFailedConnect(true),
		nats.MaxReconnects(-1),
		nats.ReconnectWait(2*nats.DefaultReconnectWait),
	)
	if err != nil {
		return nil, nil, fmt.Errorf("nats connect: %w", err)
	}

	js, err := jetstream.New(nc)
	if err != nil {
		nc.Drain()
		return nil, nil, fmt.Errorf("jetstream init: %w", err)
	}

	// Ensure CHAT stream exists (idempotent)
	ctx := context.Background()
	_, err = js.CreateOrUpdateStream(ctx, jetstream.StreamConfig{
		Name:       "CHAT",
		Subjects:   []string{"chat.>"},
		Storage:    jetstream.FileStorage,
		Replicas:   1, // increase to 3 in production with NATS cluster
		Retention:  jetstream.LimitsPolicy,
		MaxAge:     7 * 24 * 3600 * 1000000000, // 7 days in nanoseconds
		Duplicates: 2 * 60 * 1000000000,        // 2-minute dedup window
	})
	if err != nil {
		nc.Drain()
		return nil, nil, fmt.Errorf("create CHAT stream: %w", err)
	}

	cleanup := func() { nc.Drain() }
	return &NATSPublisher{js: js}, cleanup, nil
}

// Publish sends a message to the given subject with server-side dedup.
func (p *NATSPublisher) Publish(ctx context.Context, subject string, data []byte) error {
	_, err := p.js.Publish(ctx, subject, data)
	return err
}
