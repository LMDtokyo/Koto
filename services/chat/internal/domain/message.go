// Package domain defines core entities and repository interfaces for the chat service.
package domain

import (
	"context"
	"strings"
	"time"
)

// MessageType defines what kind of payload a message carries.
type MessageType uint8

const (
	MessageTypeText     MessageType = 1
	MessageTypeImage    MessageType = 2
	MessageTypeVideo    MessageType = 3
	MessageTypeAudio    MessageType = 4
	MessageTypeFile     MessageType = 5
	MessageTypeReaction MessageType = 6
	MessageTypeDeleted  MessageType = 7
)

// Message is an end-to-end encrypted message stored on the server.
// The server NEVER sees plaintext — it stores the ciphertext blob only.
type Message struct {
	ID             string      // TIMEUUID (ScyllaDB native time-based UUID)
	ConversationID string      // Conversation this message belongs to
	SenderID       string      // Sender's account ID (hex Ed25519 public key)
	Type           MessageType
	Ciphertext     []byte      // E2EE payload — opaque to the server
	SenderKeyData  []byte      // Sealed sender envelope (optional)
	ServerSeq      int64       // Monotonic server-assigned sequence number
	ClientSeq      int64       // Client-assigned sequence (for deduplication)
	SentAt         time.Time   // Server receipt time (for ordering)
	ExpiresAt      *time.Time  // Optional message TTL
	ReplyTo        string      // ID of the message being quoted; empty when none.
	EditedAt       *time.Time  // Last edit time, nil if never edited.
	ForwardedFrom  string      // Account id of the original author when this message is a forward.
	Pinned         bool        // Currently pinned to the top of the conversation.
}

// Conversation represents a 1:1 or group chat thread.
type Conversation struct {
	ID           string
	Type         ConversationType
	Name         string   // Group chats only — empty for direct conversations.
	MemberIDs    []string
	CreatedAt    time.Time
	LastMsgID    string
	LastMsgAt    time.Time
}

// ConversationType distinguishes direct and group conversations.
type ConversationType uint8

const (
	ConversationTypeDirect ConversationType = 1
	ConversationTypeGroup  ConversationType = 2
)

// MessageRepository defines persistence operations for messages.
type MessageRepository interface {
	// Save stores a new message and writes the generated ID back into m.
	Save(ctx context.Context, m *Message) error

	// GetHistory returns messages for a conversation, ordered newest-first.
	// cursor is the TIMEUUID of the oldest message seen; empty means start from latest.
	GetHistory(ctx context.Context, conversationID, cursor string, limit int) ([]Message, error)

	// GetByID returns a single message by id; ErrNotFound if missing.
	GetByID(ctx context.Context, conversationID, messageID string) (Message, error)

	// Edit replaces the ciphertext of a message and stamps edited_at.
	// The Signal session advances per-message — the new ciphertext decrypts
	// to the new plaintext under a fresh chain key.
	Edit(ctx context.Context, conversationID, messageID string, ciphertext []byte, editedAt time.Time) error

	// SetPinned flips the pinned flag on a message.
	SetPinned(ctx context.Context, conversationID, messageID string, pinned bool) error

	// ListPinned returns every pinned message in the conversation.
	ListPinned(ctx context.Context, conversationID string) ([]Message, error)

	// Delete marks a message as deleted (tombstone).
	Delete(ctx context.Context, conversationID, messageID string) error

	// SearchMeta returns messages filtered by server-visible metadata only.
	SearchMeta(ctx context.Context, in MessageSearchMetaInput) ([]Message, string, error)
}

// Reaction is a single (actor, emoji) pair on a message. A user reacting
// twice with the same emoji is treated as a no-op; reacting with a different
// emoji creates an additional row (a user can express multiple feelings).
type Reaction struct {
	ConversationID string
	MessageID      string
	ActorID        string
	Emoji          string
	ReactedAt      time.Time
}

// ReactionRepository persists reaction add/remove and bulk loads.
type ReactionRepository interface {
	// Add inserts the reaction; idempotent (same row overwrite is a no-op).
	Add(ctx context.Context, r Reaction) error

	// Remove deletes a single (actor, emoji) reaction on a message.
	Remove(ctx context.Context, conversationID, messageID, actorID, emoji string) error

	// ListForMessage returns all reactions on the given message.
	ListForMessage(ctx context.Context, conversationID, messageID string) ([]Reaction, error)
}

// ReactionEvent is published to NATS when a reaction is added or removed.
// `Added` is true on add, false on remove. Receivers update their local
// reaction list for the given message.
type ReactionEvent struct {
	ConversationID string    `json:"conv_id"`
	MessageID      string    `json:"msg_id"`
	ActorID        string    `json:"actor_id"`
	Emoji          string    `json:"emoji"`
	Added          bool      `json:"added"`
	RecipientIDs   []string  `json:"recipient_ids"`
	ReactedAt      time.Time `json:"reacted_at"`
}

// ConversationRepository defines persistence for conversation metadata.
type ConversationRepository interface {
	// Create creates a new conversation. Writes the generated ID back into c.
	Create(ctx context.Context, c *Conversation) error

	// Get retrieves a conversation by ID.
	Get(ctx context.Context, id string) (Conversation, error)

	// GetForMember returns all conversation IDs a member belongs to.
	GetForMember(ctx context.Context, memberID string) ([]string, error)

	// FindDirectBetween returns the (single) direct-conversation ID between
	// two members, if any. Empty string if none. Used to make Create
	// idempotent for direct chats (any number of clicks → same convId).
	FindDirectBetween(ctx context.Context, memberA, memberB string) (string, error)

	// UpdateLastMessage updates the last message pointer.
	UpdateLastMessage(ctx context.Context, conversationID, msgID string, at time.Time) error

	// SearchForMember returns conversations matching server-visible metadata query.
	SearchForMember(ctx context.Context, memberID, query string, limit, offset int) ([]Conversation, int, error)
}

// MessageSearchMetaInput describes server-side metadata filters (no plaintext search).
type MessageSearchMetaInput struct {
	ConversationID string
	SenderID       string
	From           *time.Time
	To             *time.Time
	Type           *MessageType
	Limit          int
	Cursor         string // message id offset
}

func (in MessageSearchMetaInput) NormalizedQuery() string {
	return strings.TrimSpace(strings.ToLower(in.ConversationID))
}

// DeliveryEvent is published to NATS when a message is ready for delivery.
// The gateway consumes this to push a WebSocket "new_message" frame to recipients.
type DeliveryEvent struct {
	MessageID      string      `json:"msg_id"`
	ConversationID string      `json:"conv_id"`
	SenderID       string      `json:"sender_id"`
	RecipientIDs   []string    `json:"recipient_ids"`
	Type           MessageType `json:"type"`
	Ciphertext     []byte      `json:"ciphertext"` // included so gateway can relay without a DB round-trip
	SentAt         time.Time   `json:"sent_at"`
	ReplyTo        string      `json:"reply_to,omitempty"`
	ForwardedFrom  string      `json:"forwarded_from,omitempty"`
}

// PinEvent is published when a message is pinned or unpinned. The receiver
// updates its local row's pinned flag.
type PinEvent struct {
	ConversationID string    `json:"conv_id"`
	MessageID      string    `json:"msg_id"`
	ActorID        string    `json:"actor_id"`
	Pinned         bool      `json:"pinned"`
	RecipientIDs   []string  `json:"recipient_ids"`
	At             time.Time `json:"at"`
}

// EditEvent is published when a message's ciphertext is replaced. Receivers
// decrypt the new ciphertext and overwrite their cached plaintext for the
// existing message id.
type EditEvent struct {
	MessageID      string    `json:"msg_id"`
	ConversationID string    `json:"conv_id"`
	SenderID       string    `json:"sender_id"`
	RecipientIDs   []string  `json:"recipient_ids"`
	Ciphertext     []byte    `json:"ciphertext"`
	EditedAt       time.Time `json:"edited_at"`
}

// ConversationCreatedEvent is published to NATS when a new conversation
// (group or direct) is created. The gateway consumes this to push a
// `conversation_created` WebSocket frame to every other member so their
// client's chat list updates without polling.
type ConversationCreatedEvent struct {
	ConversationID string           `json:"conv_id"`
	Type           ConversationType `json:"type"`
	Name           string           `json:"name"`
	CreatorID      string           `json:"creator_id"`
	MemberIDs      []string         `json:"member_ids"`
	RecipientIDs   []string         `json:"recipient_ids"` // members minus creator
	CreatedAt      time.Time        `json:"created_at"`
}
