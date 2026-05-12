// Package unified_push delivers push notifications to UnifiedPush endpoints.
// This provides FCM-free push delivery on Android (GrapheneOS, CalyxOS, etc.).
package unified_push

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

// Client sends messages to UnifiedPush distributor endpoints.
type Client struct {
	http *http.Client
}

// New creates a UnifiedPush client.
func New() *Client {
	return &Client{
		http: &http.Client{Timeout: 10 * time.Second},
	}
}

// Notify posts a notification to the device's UnifiedPush endpoint URL.
// The endpoint URL is registered by the device during setup.
func (c *Client) Notify(ctx context.Context, endpointURL, conversationID string) error {
	payload, err := json.Marshal(map[string]any{
		"conv_id": conversationID,
		// UnifiedPush payload must be ≤4KB; we keep it minimal
	})
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpointURL, bytes.NewReader(payload))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("TTL", "600") // 10 minutes
	req.Header.Set("Urgency", "high")

	resp, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("unified_push: post: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		return fmt.Errorf("unified_push: server returned %d", resp.StatusCode)
	}
	return nil
}
