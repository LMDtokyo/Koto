// Package domain defines user profile and contacts entities.
package domain

import (
	"context"
	"encoding/json"
	"time"
)

// Profile is the optional user-facing identity layer.
// All fields are optional — users remain anonymous by default.
type Profile struct {
	AccountID   string    `json:"account_id"`
	DisplayName string    `json:"display_name"`           // Optional nickname
	AvatarURL   string    `json:"avatar_url"`             // Optional avatar (media service URL)
	BannerURL   string    `json:"banner_url"`             // Optional banner / header image
	Bio         string    `json:"bio"`                    // Optional short bio
	Username    string    `json:"username"`               // Optional public @handle (case-insensitive unique)
	UpdatedAt   time.Time `json:"-"`                       // Wire format below.
}

// MarshalJSON emits `updated_at` as a unix timestamp in seconds — the desktop
// client deserialises it as a Long. Default `time.Time` JSON would be RFC3339
// and break the DTO.
func (p Profile) MarshalJSON() ([]byte, error) {
	type alias Profile
	return json.Marshal(struct {
		alias
		UpdatedAt int64 `json:"updated_at"`
	}{
		alias:     alias(p),
		UpdatedAt: p.UpdatedAt.Unix(),
	})
}

// Contact represents a bilateral relationship between two accounts.
type Contact struct {
	OwnerID   string    // The account who added the contact
	ContactID string    // The account that was added
	Nickname  string    // Local alias for this contact
	AddedAt   time.Time
	Blocked   bool
}

type FriendRequest struct {
	FromID    string
	ToID      string
	Status    string
	CreatedAt time.Time
	UpdatedAt time.Time
}

// AcceptedFriendEdge is one accepted friendship row; PeerID is the other account.
type AcceptedFriendEdge struct {
	PeerID    string
	UpdatedAt time.Time
}

// ProfileRepository persists user profiles.
type ProfileRepository interface {
	// Upsert creates or updates a profile.
	Upsert(ctx context.Context, p Profile) error

	// Get returns a profile by account ID.
	Get(ctx context.Context, accountID string) (Profile, error)

	// GetBatch returns profiles for multiple account IDs.
	GetBatch(ctx context.Context, accountIDs []string) ([]Profile, error)

	// IsUsernameTaken reports whether the given username (case-insensitive)
	// is already claimed by some other account. Self-reuse counts as available.
	IsUsernameTaken(ctx context.Context, username, byAccountID string) (bool, error)

	// SetUsername stores a username on the profile, creating the row if needed.
	// Returns ErrAlreadyExists when the username collides with another account.
	SetUsername(ctx context.Context, accountID, username string) error

	// FindByUsername returns the profile claiming [username] (case-insensitive).
	// Returns ErrNotFound if no profile holds it.
	FindByUsername(ctx context.Context, username string) (Profile, error)

	// Search returns public profiles by username/display name query with offset pagination.
	// nextOffset is -1 when there are no more results.
	Search(ctx context.Context, query string, limit, offset int) (profiles []Profile, nextOffset int, err error)
}

// PrekeyBundle holds the Signal-Protocol key material an account publishes so
// peers can establish an X3DH + PQXDH session without both parties being online.
type PrekeyBundle struct {
    AccountID      string
    RegistrationID uint32
    IdentityKey    []byte
    SignedPrekeyID uint32
    SignedPrekeyPub []byte
    SignedPrekeySig []byte
    KyberPrekeyID  uint32
    KyberPrekeyPub []byte
    KyberPrekeySig []byte
}

// OneTimePrekey is a single-use EC prekey from the account's prekey pool.
type OneTimePrekey struct {
    AccountID string
    PrekeyID  uint32
    PublicKey []byte
}

// PrekeyRepository manages Signal Protocol key material.
type PrekeyRepository interface {
    // SaveBundle upserts the long-lived key bundle for an account.
    SaveBundle(ctx context.Context, b PrekeyBundle) error

    // SaveOneTimePrekeys appends one-time prekeys to the account's pool.
    SaveOneTimePrekeys(ctx context.Context, accountID string, keys []OneTimePrekey) error

    // GetBundle returns the prekey bundle for an account.
    // found is false when the account has never uploaded keys.
    GetBundle(ctx context.Context, accountID string) (PrekeyBundle, bool, error)

    // PopOneTimePrekey atomically removes and returns one prekey from the pool.
    // Returns nil if the pool is empty.
    PopOneTimePrekey(ctx context.Context, accountID string) (*OneTimePrekey, error)
}

// ContactRepository manages the contact list.
type ContactRepository interface {
	// Add creates a contact relationship.
	Add(ctx context.Context, c Contact) error

	// Remove deletes a contact.
	Remove(ctx context.Context, ownerID, contactID string) error

	// List returns all contacts for ownerID.
	List(ctx context.Context, ownerID string) ([]Contact, error)

	// Block marks a contact as blocked (or creates a blocked entry).
	Block(ctx context.Context, ownerID, contactID string) error

	// Unblock removes a block.
	Unblock(ctx context.Context, ownerID, contactID string) error

	// SendFriendRequest creates a pending request from fromID to toID.
	SendFriendRequest(ctx context.Context, fromID, toID string) error

	// ListIncomingFriendRequests returns pending requests for accountID.
	ListIncomingFriendRequests(ctx context.Context, accountID string) ([]FriendRequest, error)

	// ListOutgoingFriendRequests returns pending requests sent by accountID.
	ListOutgoingFriendRequests(ctx context.Context, accountID string) ([]FriendRequest, error)

	// ListAcceptedFriendEdges returns accepted friendships involving accountID (peer is the other side).
	ListAcceptedFriendEdges(ctx context.Context, accountID string) ([]AcceptedFriendEdge, error)

	// AcceptFriendRequest marks pending request from fromID to toID as accepted.
	AcceptFriendRequest(ctx context.Context, fromID, toID string) error

	// RejectFriendRequest marks pending request as rejected.
	RejectFriendRequest(ctx context.Context, fromID, toID string) error

	// FriendRelation returns one of: none, outgoing_pending, incoming_pending, accepted.
	FriendRelation(ctx context.Context, accountID, peerID string) (string, error)

	// CanMessage reports whether users can message each other.
	CanMessage(ctx context.Context, accountID, peerID string) (bool, error)
}
