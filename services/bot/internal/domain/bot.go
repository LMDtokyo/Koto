// Package domain defines bot entities and repository interfaces.
package domain

import (
	"context"
	"time"
)

// Bot is a registered bot application (similar to Telegram Bot API).
type Bot struct {
	ID          string    // random hex ID
	AccountID   string    // the bot's account in the messenger
	OwnerID     string    // developer account that registered the bot
	Name        string    // display name
	Username    string    // unique handle (e.g. @translate_bot)
	Token       string    // secret token for webhook authentication
	WebhookURL  string    // URL to POST events to
	Active      bool
	CreatedAt   time.Time
}

// BotEvent is delivered to the bot's webhook when a user sends a message.
type BotEvent struct {
	EventID    string    `json:"event_id"`
	BotID      string    `json:"bot_id"`
	SenderID   string    `json:"sender_id"`
	ConvID     string    `json:"conv_id"`
	Type       string    `json:"type"`   // "message", "command"
	Text       string    `json:"text"`   // decrypted on client side before forwarding (optional)
	SentAt     time.Time `json:"sent_at"`
}

// BotRepository persists bot registrations.
type BotRepository interface {
	Create(ctx context.Context, b Bot) error
	Get(ctx context.Context, id string) (Bot, error)
	GetByUsername(ctx context.Context, username string) (Bot, error)
	GetByToken(ctx context.Context, token string) (Bot, error)
	ListByOwner(ctx context.Context, ownerID string) ([]Bot, error)
	Update(ctx context.Context, b Bot) error
	Delete(ctx context.Context, id string) error
}
