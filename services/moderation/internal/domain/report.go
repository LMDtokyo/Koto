// Package domain — типы и интерфейсы предметной области moderation.
package domain

import (
	"context"
	"time"
)

// Report — одна жалоба от пользователя на сообщение/контакт.
type Report struct {
	ID                  string
	ReporterID          string
	ReportedID          string
	ConversationID      string
	MessageID           string
	Reason              string
	Plaintext           string
	Context             []ContextMessage
	Classification      string
	ClassificationScore float64
	Status              string // pending | reviewed | dismissed | actioned
	ModeratorID         string
	ModeratorNote       string
	CreatedAt           time.Time
	ReviewedAt          *time.Time
}

// ContextMessage — одно из 5 предшествующих сообщений в treadе.
type ContextMessage struct {
	MessageID string `json:"message_id"`
	SenderID  string `json:"sender_id"`
	Plaintext string `json:"plaintext"`
	SentAt    int64  `json:"sent_at"`
}

// Action — наказание, применённое к аккаунту.
type Action struct {
	AccountID   string
	Action      string // warn | mute | ban | shadowban
	Reason      string
	ModeratorID string
	ExpiresAt   *time.Time
	CreatedAt   time.Time
}

// ReportRepository — persistent storage for reports.
type ReportRepository interface {
	Create(ctx context.Context, r Report) (Report, error)
	GetByID(ctx context.Context, id string) (Report, error)
	ListByStatus(ctx context.Context, status string, limit int) ([]Report, error)
	UpdateStatus(ctx context.Context, id, status, moderatorID, note string) error
}

// ActionRepository — persistent storage for moderator actions.
type ActionRepository interface {
	Upsert(ctx context.Context, a Action) error
	Get(ctx context.Context, accountID string) (Action, error)
	Remove(ctx context.Context, accountID string) error
}

// Classifier — opaque interface к ML-классификатору (OpenAI Moderation, локальный, etc).
type Classifier interface {
	Classify(ctx context.Context, text string) (category string, score float64, err error)
}
