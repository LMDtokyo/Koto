// Package token issues and validates Ed25519-signed JWT access tokens.
package token

import (
	"crypto/ed25519"
	"encoding/hex"
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// Claims is the JWT payload stored in access tokens.
type Claims struct {
	AccountID string `json:"aid"`
	jwt.RegisteredClaims
}

// Manager signs and validates JWT tokens using Ed25519.
// A single Manager instance is safe for concurrent use.
type Manager struct {
	privateKey ed25519.PrivateKey
	publicKey  ed25519.PublicKey
	accessTTL  time.Duration
}

// NewManager creates a Manager from a 32-byte Ed25519 seed (hex-encoded).
// Panics if seedHex is not a valid 32-byte hex string.
func NewManager(seedHex string, accessTTL time.Duration) (*Manager, error) {
	seed, err := hex.DecodeString(seedHex)
	if err != nil {
		return nil, errors.New("JWT seed: invalid hex")
	}
	if len(seed) != ed25519.SeedSize {
		return nil, errors.New("JWT seed: must be 32 bytes")
	}
	priv := ed25519.NewKeyFromSeed(seed)
	return &Manager{
		privateKey: priv,
		publicKey:  priv.Public().(ed25519.PublicKey),
		accessTTL:  accessTTL,
	}, nil
}

// NewManagerFromPublicKey creates a verify-only Manager (no signing capability).
// publicKeyHex is a hex-encoded 32-byte Ed25519 public key.
func NewManagerFromPublicKey(publicKeyHex string) (*Manager, error) {
	pub, err := hex.DecodeString(publicKeyHex)
	if err != nil {
		return nil, errors.New("JWT public key: invalid hex")
	}
	if len(pub) != ed25519.PublicKeySize {
		return nil, errors.New("JWT public key: must be 32 bytes")
	}
	return &Manager{publicKey: ed25519.PublicKey(pub)}, nil
}

// Issue creates a signed access token for accountID.
func (m *Manager) Issue(accountID string) (string, error) {
	if m.privateKey == nil {
		return "", errors.New("manager has no private key")
	}
	now := time.Now().UTC()
	claims := Claims{
		AccountID: accountID,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   accountID,
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(m.accessTTL)),
		},
	}
	tok := jwt.NewWithClaims(jwt.SigningMethodEdDSA, claims)
	return tok.SignedString(m.privateKey)
}

// verifyLeeway tolerates ±30 seconds of clock skew between the device that
// issued the token and the one verifying it. Real users routinely have phones
// with NTP off by tens of seconds; without leeway a freshly issued token
// could fail "token not yet valid" or "expired" right at the boundary.
const verifyLeeway = 30 * time.Second

// Verify parses and validates a signed access token.
func (m *Manager) Verify(tokenStr string) (*Claims, error) {
	tok, err := jwt.ParseWithClaims(tokenStr, &Claims{},
		func(t *jwt.Token) (any, error) {
			if _, ok := t.Method.(*jwt.SigningMethodEd25519); !ok {
				return nil, errors.New("unexpected signing method")
			}
			return m.publicKey, nil
		},
		jwt.WithExpirationRequired(),
		jwt.WithLeeway(verifyLeeway),
	)
	if err != nil {
		return nil, err
	}
	claims, ok := tok.Claims.(*Claims)
	if !ok || !tok.Valid {
		return nil, errors.New("invalid token claims")
	}
	return claims, nil
}
