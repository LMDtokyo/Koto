// Package apns wraps the sideshow/apns2 library for iOS push delivery.
package apns

import (
	"fmt"

	"github.com/sideshow/apns2"
	"github.com/sideshow/apns2/token"
)

// Client wraps apns2.Client.
type Client struct {
	inner    *apns2.Client
	bundleID string
}

// NewClient creates a production or sandbox APNs client using the p8 key file.
func NewClient(keyPath, keyID, teamID, bundleID string, production bool) (*Client, error) {
	authKey, err := token.AuthKeyFromFile(keyPath)
	if err != nil {
		return nil, fmt.Errorf("apns: load key: %w", err)
	}

	t := &token.Token{
		AuthKey: authKey,
		KeyID:   keyID,
		TeamID:  teamID,
	}

	c := apns2.NewTokenClient(t)
	if production {
		c = c.Production()
	} else {
		c = c.Development()
	}

	return &Client{inner: c, bundleID: bundleID}, nil
}

// SendSilent sends a silent push (content-available) to wake the app.
// The app fetches and decrypts new messages itself — APNs sees no message content.
func (c *Client) SendSilent(deviceToken, conversationID string, badgeCount int) error {
	n := &apns2.Notification{
		DeviceToken: deviceToken,
		Topic:       c.bundleID,
		Priority:    apns2.PriorityLow, // silent pushes must use low priority
		PushType:    apns2.PushTypeBackground,
		Payload:     buildSilentPayload(conversationID, badgeCount),
	}

	res, err := c.inner.Push(n)
	if err != nil {
		return fmt.Errorf("apns: push: %w", err)
	}
	if !res.Sent() {
		return fmt.Errorf("apns: rejected: %s (status %d)", res.Reason, res.StatusCode)
	}
	return nil
}

func buildSilentPayload(conversationID string, badge int) map[string]any {
	return map[string]any{
		"aps": map[string]any{
			"content-available": 1,
			"badge":             badge,
		},
		"conv_id": conversationID,
	}
}
