// Package domain defines the core entities and repository interfaces for the auth service.
// This layer has zero external dependencies — only pure Go.
package domain

import (
	"context"
	"time"
)

// Account is an anonymous account identified solely by its Ed25519 public key.
// No phone number, email, or username is required.
type Account struct {
	ID        string    // hex-encoded Ed25519 public key (32 bytes = 64 hex chars)
	CreatedAt time.Time

	// Signal Protocol key material (stored for key distribution / pre-key bundles)
	IdentityKey       []byte // Ed25519 public key (32 bytes)
	SignedPreKey      []byte // X25519 signed pre-key (32 bytes)
	SignedPreKeySig   []byte // Ed25519 signature over SignedPreKey (64 bytes)
	SignedPreKeyID    uint32
}

// OneTimePreKey is a single-use X25519 key published by the client.
// Consumed during X3DH session initiation.
type OneTimePreKey struct {
	ID        uint32
	AccountID string
	KeyData   []byte // X25519 public key (32 bytes)
	Used      bool
}

// AccountRepository persists and retrieves accounts.
type AccountRepository interface {
	// Create stores a new account. Returns ErrAlreadyExists if ID is taken.
	Create(ctx context.Context, a Account) error

	// Get retrieves an account by ID. Returns ErrNotFound if absent.
	Get(ctx context.Context, id string) (Account, error)

	// Exists reports whether an account with the given ID exists.
	Exists(ctx context.Context, id string) (bool, error)

	// UpdateKeys replaces the signed pre-key (and its signature/id) on an
	// existing account during seed-restore. Identity key never changes.
	UpdateKeys(ctx context.Context, id string, signedPreKey, signedPreKeySig []byte, signedPreKeyID uint32) error
}

// PreKeyRepository manages one-time pre-keys.
type PreKeyRepository interface {
	// Save bulk-stores new one-time pre-keys for an account.
	Save(ctx context.Context, keys []OneTimePreKey) error

	// Pop atomically retrieves and marks a single unused pre-key as used.
	// Returns ErrNotFound when none remain.
	Pop(ctx context.Context, accountID string) (OneTimePreKey, error)

	// Count returns the number of unused pre-keys for an account.
	Count(ctx context.Context, accountID string) (int, error)

	// Replace atomically wipes the entire pre-key pool for accountID and
	// inserts the supplied set in a single transaction. Used during seed
	// restore so the new device gets a clean OTPK pool keyed off its fresh
	// random material rather than the previous device's leftovers.
	Replace(ctx context.Context, accountID string, keys []OneTimePreKey) error
}

// Session represents one active device the user is logged in on.
// Stable id; refresh_token_hash rotates with each refresh-token rotation.
type Session struct {
	ID               string
	AccountID        string
	RefreshTokenHash string
	DeviceName       string
	Platform         string
	AppVersion       string
	ClientIP         string
	CreatedAt        time.Time
	LastSeenAt       time.Time
}

// SessionRepository persists active device sessions.
type SessionRepository interface {
	// Create inserts a new session row. Caller supplies a stable id (uuid)
	// and the refresh token hash; server-side fields (timestamps) are filled.
	Create(ctx context.Context, s Session) error

	// ListForAccount returns all active sessions for [accountID], newest
	// activity first.
	ListForAccount(ctx context.Context, accountID string) ([]Session, error)

	// GetByRefreshTokenHash returns the session bound to [hash]. Used to
	// resolve "current device" during list and during refresh rotation.
	GetByRefreshTokenHash(ctx context.Context, hash string) (Session, error)

	// Touch updates [last_seen_at] and rotates [refresh_token_hash] on
	// successful refresh. The device_name/platform/app_version/client_ip
	// fields are upgraded only when they are currently blank in the row —
	// this lets a legacy row (created without device metadata) self-heal on
	// first refresh without overwriting metadata captured at register time.
	Touch(ctx context.Context, sessionID, newRefreshTokenHash, deviceName, platform, appVersion, clientIP string) error

	// RevokeByID removes the session row. Caller is responsible for
	// invalidating the matching refresh token in the cache.
	RevokeByID(ctx context.Context, accountID, sessionID string) (Session, error)

	// RevokeOthers removes every session for [accountID] EXCEPT
	// [keepSessionID], returning the affected rows so the caller can
	// invalidate their refresh tokens.
	RevokeOthers(ctx context.Context, accountID, keepSessionID string) ([]Session, error)
}

// RefreshTokenRepository manages refresh token state.
type RefreshTokenRepository interface {
	// Store saves a refresh token with TTL.
	Store(ctx context.Context, token, accountID string, ttl time.Duration) error

	// Lookup returns the accountID bound to token. Returns ErrNotFound if invalid/expired.
	Lookup(ctx context.Context, token string) (string, error)

	// Revoke invalidates a refresh token.
	Revoke(ctx context.Context, token string) error

	// RememberRotation caches the freshly issued token pair against the old
	// refresh token for [ttl] so a retry-after-network-loss with the same old
	// token yields the same new pair instead of an "already rotated" error.
	RememberRotation(ctx context.Context, oldToken string, payload []byte, ttl time.Duration) error

	// LookupRotation returns a previously cached rotation payload, or ErrNotFound.
	LookupRotation(ctx context.Context, oldToken string) ([]byte, error)
}
