// Package domain defines push notification entities.
package domain

import (
	"context"
	"time"
)

// Platform identifies the client operating system.
type Platform uint8

const (
	PlatformIOS     Platform = 1
	PlatformAndroid Platform = 2
)

// Device is a registered push notification target.
type Device struct {
	ID        string
	AccountID string
	Token     string    // APNs device token or UnifiedPush endpoint URL
	Platform  Platform
	BundleID  string    // iOS bundle ID or Android package name
	UpdatedAt time.Time
}

// DeviceRepository persists device push tokens.
type DeviceRepository interface {
	// Upsert registers or updates a push token for an account.
	Upsert(ctx context.Context, d Device) error

	// ListForAccount returns all active devices for an account.
	ListForAccount(ctx context.Context, accountID string) ([]Device, error)

	// Remove deletes a device token (on logout or token rotation).
	Remove(ctx context.Context, deviceID string) error

	// RemoveToken deletes by token value (on APNs Unregistered response).
	RemoveToken(ctx context.Context, token string) error
}
