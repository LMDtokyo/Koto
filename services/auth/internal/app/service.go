// Package app contains the application use-cases for the auth service.
package app

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"time"

	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/pkg/token"
	"github.com/koto-messenger/koto/services/auth/internal/crypto"
	"github.com/koto-messenger/koto/services/auth/internal/domain"
)

// rotationReplayWindow is how long a freshly issued token pair stays
// retrievable by the *old* refresh token. Lets a client whose response was
// lost on the wire retry with the same old token and get the same new pair
// instead of being booted to re-auth.
const rotationReplayWindow = 30 * time.Second

// Service orchestrates auth use-cases.
type Service struct {
	accounts   domain.AccountRepository
	preKeys    domain.PreKeyRepository
	refreshes  domain.RefreshTokenRepository
	sessions   domain.SessionRepository
	tokens     *token.Manager
	refreshTTL time.Duration
}

// New creates the auth application service.
func New(
	accounts domain.AccountRepository,
	preKeys domain.PreKeyRepository,
	refreshes domain.RefreshTokenRepository,
	sessions domain.SessionRepository,
	tokens *token.Manager,
	refreshTTL time.Duration,
) *Service {
	return &Service{
		accounts:   accounts,
		preKeys:    preKeys,
		refreshes:  refreshes,
		sessions:   sessions,
		tokens:     tokens,
		refreshTTL: refreshTTL,
	}
}

// DeviceInfo describes the device that initiated a register/restore — used
// to populate the linked-devices list. Empty fields are tolerated; the UI
// falls back to a generic "unknown device" label.
type DeviceInfo struct {
	Name       string
	Platform   string
	AppVersion string
	ClientIP   string
}

// RegisterInput is the client payload for creating a new anonymous account.
type RegisterInput struct {
	IdentityKey     []byte   // Ed25519 public key (32 bytes)
	SignedPreKey    []byte   // X25519 signed pre-key (32 bytes)
	SignedPreKeySig []byte   // Ed25519 sig over SignedPreKey (64 bytes)
	SignedPreKeyID  uint32
	OneTimePreKeys  [][]byte // X25519 one-time pre-keys
	Device          DeviceInfo
}

// TokenPair is returned after successful registration or token refresh.
type TokenPair struct {
	AccountID    string
	SessionID    string
	AccessToken  string
	RefreshToken string
	ExpiresAt    time.Time
}

// Register creates a new anonymous account from a libsignal Curve25519 (DJB)
// identity key. The signed pre-key signature is verified via XEdDSA against
// the supplied identity key — proves the caller actually owns the matching
// private key (not just knows a public key off the wire).
func (s *Service) Register(ctx context.Context, in RegisterInput) (TokenPair, error) {
	if err := validateKeyMaterial(in); err != nil {
		return TokenPair{}, err
	}
	if !crypto.Verify(in.IdentityKey, signedPrekeyMessage(in.SignedPreKey), in.SignedPreKeySig) {
		return TokenPair{}, apperrors.New(apperrors.ErrUnauthorized, "signed_pre_key_sig does not verify against identity_key")
	}

	accountID := hex.EncodeToString(in.IdentityKey)

	account := domain.Account{
		ID:              accountID,
		CreatedAt:       time.Now().UTC(),
		IdentityKey:     in.IdentityKey,
		SignedPreKey:    in.SignedPreKey,
		SignedPreKeySig: in.SignedPreKeySig,
		SignedPreKeyID:  in.SignedPreKeyID,
	}

	if err := s.accounts.Create(ctx, account); err != nil {
		return TokenPair{}, err
	}

	if len(in.OneTimePreKeys) > 0 {
		if err := s.preKeys.Save(ctx, oneTimeKeys(accountID, in.OneTimePreKeys, 0)); err != nil {
			return TokenPair{}, err
		}
	}

	return s.issueNewSessionTokens(ctx, accountID, in.Device)
}

// Restore re-establishes a session for an existing account whose owner can
// prove control of the identity private key by signing a fresh signed pre-key
// with it. The stored identity_key is what the signature is verified against,
// so a malicious caller who only knows the account_id (which is public) can't
// spoof — they'd need the matching XEdDSA-signing scalar.
func (s *Service) Restore(ctx context.Context, in RegisterInput) (TokenPair, error) {
	if err := validateKeyMaterial(in); err != nil {
		return TokenPair{}, err
	}

	accountID := hex.EncodeToString(in.IdentityKey)
	stored, err := s.accounts.Get(ctx, accountID)
	if err != nil {
		return TokenPair{}, err
	}

	// Bind the verifier to the *stored* identity_key, not the request's, so a
	// caller can't substitute a different identity_key whose private half they
	// happen to know.
	if !crypto.Verify(stored.IdentityKey, signedPrekeyMessage(in.SignedPreKey), in.SignedPreKeySig) {
		return TokenPair{}, apperrors.New(apperrors.ErrUnauthorized, "signed_pre_key_sig does not verify against stored identity_key")
	}

	if err := s.accounts.UpdateKeys(ctx, accountID, in.SignedPreKey, in.SignedPreKeySig, in.SignedPreKeyID); err != nil {
		return TokenPair{}, err
	}
	if err := s.preKeys.Replace(ctx, accountID, oneTimeKeys(accountID, in.OneTimePreKeys, 0)); err != nil {
		return TokenPair{}, err
	}

	return s.issueNewSessionTokens(ctx, accountID, in.Device)
}

// signedPrekeyMessage reconstructs the byte sequence libsignal's Rust
// PrivateKey::calculate_signature actually signed: the 33-byte DJB-prefixed
// public key (0x05 || X25519). The wire format strips this prefix to keep
// the payload at 32 bytes; signature verification has to put it back.
func signedPrekeyMessage(pub []byte) []byte {
	out := make([]byte, 0, len(pub)+1)
	out = append(out, 0x05)
	out = append(out, pub...)
	return out
}

func validateKeyMaterial(in RegisterInput) error {
	if len(in.IdentityKey) != ed25519.PublicKeySize {
		return apperrors.New(apperrors.ErrInvalidInput, "identity_key must be 32 bytes")
	}
	if len(in.SignedPreKey) != 32 {
		return apperrors.New(apperrors.ErrInvalidInput, "signed_pre_key must be 32 bytes")
	}
	if len(in.SignedPreKeySig) != ed25519.SignatureSize {
		return apperrors.New(apperrors.ErrInvalidInput, "signed_pre_key_sig must be 64 bytes")
	}
	return nil
}

func oneTimeKeys(accountID string, raw [][]byte, idOffset int) []domain.OneTimePreKey {
	out := make([]domain.OneTimePreKey, len(raw))
	for i, k := range raw {
		out[i] = domain.OneTimePreKey{
			ID:        uint32(idOffset + i + 1),
			AccountID: accountID,
			KeyData:   k,
		}
	}
	return out
}

// RefreshTokens exchanges a valid refresh token for a new token pair.
//
// Idempotent within [rotationReplayWindow]: if the same old refresh token is
// presented twice (e.g. the first response was lost on the wire), the second
// call returns the same new pair the first call produced.
//
// The session row is touched (last_seen_at + new refresh_token_hash) on
// every successful rotation, so the linked-devices list reflects activity.
//
// [device] is only consulted when the refresh resolves to no session row
// (legacy clients) — in that case we mint a fresh session using the device
// metadata so the linked-devices list shows a recognisable name instead of
// a blank "Неизвестное устройство".
func (s *Service) RefreshTokens(ctx context.Context, refreshToken string, device DeviceInfo) (TokenPair, error) {
	if cached, err := s.refreshes.LookupRotation(ctx, refreshToken); err == nil {
		var pair TokenPair
		if jsonErr := json.Unmarshal(cached, &pair); jsonErr == nil {
			return pair, nil
		}
		// Fall through on malformed cache entry — issue a fresh pair below.
	} else if !apperrors.Is(err, apperrors.ErrNotFound) {
		return TokenPair{}, err
	}

	accountID, err := s.refreshes.Lookup(ctx, refreshToken)
	if err != nil {
		return TokenPair{}, apperrors.Wrap(apperrors.ErrUnauthorized, "invalid or expired refresh token", err)
	}

	// Resolve the session this refresh token belongs to so we can rotate
	// its hash in place. A missing row is recoverable — we can mint a
	// fresh session — but it implies legacy data, so log it.
	session, err := s.sessions.GetByRefreshTokenHash(ctx, hashRefreshToken(refreshToken))
	if err != nil && !apperrors.Is(err, apperrors.ErrNotFound) {
		return TokenPair{}, err
	}

	if err := s.refreshes.Revoke(ctx, refreshToken); err != nil {
		return TokenPair{}, err
	}

	var pair TokenPair
	if session.ID != "" {
		pair, err = s.rotateSessionTokens(ctx, session.ID, accountID, device)
	} else {
		pair, err = s.issueNewSessionTokens(ctx, accountID, device)
	}
	if err != nil {
		return TokenPair{}, err
	}

	if payload, mErr := json.Marshal(pair); mErr == nil {
		// Best-effort cache; never fail the rotation because cache write hiccupped.
		_ = s.refreshes.RememberRotation(ctx, refreshToken, payload, rotationReplayWindow)
	}
	return pair, nil
}

// RevokeToken invalidates a refresh token (logout). Also drops the session
// row for the device so the user no longer sees themselves in the linked
// devices list.
func (s *Service) RevokeToken(ctx context.Context, refreshToken string) error {
	hash := hashRefreshToken(refreshToken)
	if session, err := s.sessions.GetByRefreshTokenHash(ctx, hash); err == nil {
		_, _ = s.sessions.RevokeByID(ctx, session.AccountID, session.ID)
	}
	return s.refreshes.Revoke(ctx, refreshToken)
}

// SessionView is the wire shape returned by ListSessions — refresh token
// hashes never leave the server.
type SessionView struct {
	ID         string    `json:"id"`
	DeviceName string    `json:"device_name"`
	Platform   string    `json:"platform"`
	AppVersion string    `json:"app_version"`
	ClientIP   string    `json:"client_ip"`
	CreatedAt  time.Time `json:"created_at"`
	LastSeenAt time.Time `json:"last_seen_at"`
}

// ListSessions returns every active session for [accountID]. The client
// knows its own session_id (returned from register/restore) and marks the
// "current device" in the UI itself — the server stays simple.
func (s *Service) ListSessions(ctx context.Context, accountID string) ([]SessionView, error) {
	rows, err := s.sessions.ListForAccount(ctx, accountID)
	if err != nil {
		return nil, err
	}
	out := make([]SessionView, len(rows))
	for i, r := range rows {
		out[i] = SessionView{
			ID:         r.ID,
			DeviceName: r.DeviceName,
			Platform:   r.Platform,
			AppVersion: r.AppVersion,
			ClientIP:   r.ClientIP,
			CreatedAt:  r.CreatedAt,
			LastSeenAt: r.LastSeenAt,
		}
	}
	return out, nil
}

// RevokeSession signs out a single device by deleting its session row.
// Future refresh attempts from that device will fail to find the row and
// return 401 — the client's WS will pick that up and clear local session.
func (s *Service) RevokeSession(ctx context.Context, accountID, sessionID string) error {
	_, err := s.sessions.RevokeByID(ctx, accountID, sessionID)
	return err
}

// RevokeOtherSessions revokes every session for [accountID] except the one
// identified by [keepSessionID].
func (s *Service) RevokeOtherSessions(ctx context.Context, accountID, keepSessionID string) (int, error) {
	revoked, err := s.sessions.RevokeOthers(ctx, accountID, keepSessionID)
	if err != nil {
		return 0, err
	}
	return len(revoked), nil
}

// FetchPreKeyBundle retrieves the key material needed to initiate an X3DH session.
func (s *Service) FetchPreKeyBundle(ctx context.Context, accountID string) (domain.Account, domain.OneTimePreKey, error) {
	acc, err := s.accounts.Get(ctx, accountID)
	if err != nil {
		return domain.Account{}, domain.OneTimePreKey{}, err
	}

	otk, err := s.preKeys.Pop(ctx, accountID)
	if err != nil && !apperrors.Is(err, apperrors.ErrNotFound) {
		return domain.Account{}, domain.OneTimePreKey{}, err
	}
	// otk may be zero-value when depleted — callers must handle this

	return acc, otk, nil
}

// PublishPreKeys adds new one-time pre-keys for an existing account.
func (s *Service) PublishPreKeys(ctx context.Context, accountID string, keys [][]byte) (int, error) {
	if _, err := s.accounts.Get(ctx, accountID); err != nil {
		return 0, err
	}

	count, err := s.preKeys.Count(ctx, accountID)
	if err != nil {
		return 0, err
	}

	if err := s.preKeys.Save(ctx, oneTimeKeys(accountID, keys, count)); err != nil {
		return 0, err
	}

	return count + len(keys), nil
}

// ValidateToken verifies an access token and returns the accountID.
func (s *Service) ValidateToken(ctx context.Context, accessToken string) (string, error) {
	claims, err := s.tokens.Verify(accessToken)
	if err != nil {
		return "", apperrors.Wrap(apperrors.ErrUnauthorized, "invalid access token", err)
	}
	return claims.AccountID, nil
}

// ─── helpers ─────────────────────────────────────────────────────────────────

// issueNewSessionTokens issues a fresh token pair AND creates a new session
// row tied to the supplied device. Used by register/restore — every fresh
// auth flow starts a new session.
func (s *Service) issueNewSessionTokens(ctx context.Context, accountID string, dev DeviceInfo) (TokenPair, error) {
	accessToken, err := s.tokens.Issue(accountID)
	if err != nil {
		return TokenPair{}, fmt.Errorf("issue access token: %w", err)
	}
	refreshToken, err := generateSecureToken()
	if err != nil {
		return TokenPair{}, fmt.Errorf("generate refresh token: %w", err)
	}
	if err := s.refreshes.Store(ctx, refreshToken, accountID, s.refreshTTL); err != nil {
		return TokenPair{}, err
	}

	sessionID, err := generateSessionID()
	if err != nil {
		return TokenPair{}, fmt.Errorf("generate session id: %w", err)
	}
	session := domain.Session{
		ID:               sessionID,
		AccountID:        accountID,
		RefreshTokenHash: hashRefreshToken(refreshToken),
		DeviceName:       dev.Name,
		Platform:         dev.Platform,
		AppVersion:       dev.AppVersion,
		ClientIP:         dev.ClientIP,
	}
	if err := s.sessions.Create(ctx, session); err != nil {
		return TokenPair{}, err
	}

	return TokenPair{
		AccountID:    accountID,
		SessionID:    sessionID,
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		ExpiresAt:    time.Now().UTC().Add(s.refreshTTL),
	}, nil
}

// rotateSessionTokens issues a fresh pair AND rotates the binding on an
// existing session. Used by RefreshTokens — same logical session, fresh
// credentials. [device] is forwarded so blank metadata on a legacy session
// row is back-filled on the first refresh.
func (s *Service) rotateSessionTokens(ctx context.Context, sessionID, accountID string, device DeviceInfo) (TokenPair, error) {
	accessToken, err := s.tokens.Issue(accountID)
	if err != nil {
		return TokenPair{}, fmt.Errorf("issue access token: %w", err)
	}
	refreshToken, err := generateSecureToken()
	if err != nil {
		return TokenPair{}, fmt.Errorf("generate refresh token: %w", err)
	}
	if err := s.refreshes.Store(ctx, refreshToken, accountID, s.refreshTTL); err != nil {
		return TokenPair{}, err
	}
	if err := s.sessions.Touch(ctx, sessionID, hashRefreshToken(refreshToken),
		device.Name, device.Platform, device.AppVersion, device.ClientIP); err != nil {
		return TokenPair{}, err
	}
	return TokenPair{
		AccountID:    accountID,
		SessionID:    sessionID,
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		ExpiresAt:    time.Now().UTC().Add(s.refreshTTL),
	}, nil
}

func generateSecureToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// generateSessionID returns a random 128-bit hex string. Not a UUID format
// strictly — but uniqueness, not RFC-compliance, is the contract.
func generateSessionID() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// hashRefreshToken returns the hex SHA-256 digest of [token]. We store this
// (not the raw token) so a postgres dump leak does not yield active
// credentials. The cache (Dragonfly) still holds the raw token for the
// online lookup path.
func hashRefreshToken(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}
