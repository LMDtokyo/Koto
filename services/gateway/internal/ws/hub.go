// Package ws provides a WebSocket hub for real-time message delivery.
package ws

import (
	"context"
	"sync"

	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
	"github.com/rs/zerolog"
)

// Envelope is the wire format for WebSocket messages.
type Envelope struct {
	Type    string `json:"type"`
	Payload any    `json:"payload"`
}

// Client represents a connected WebSocket session.
type Client struct {
	AccountID string
	conn      *websocket.Conn
	send      chan Envelope
}

// Hub manages all active WebSocket connections.
// It is safe for concurrent use.
type Hub struct {
	mu      sync.RWMutex
	clients map[string][]*Client // accountID → []clients (multi-device)
	log     zerolog.Logger
}

// NewHub creates a new Hub.
func NewHub(log zerolog.Logger) *Hub {
	return &Hub{
		clients: make(map[string][]*Client),
		log:     log,
	}
}

// Register adds a client to the hub.
func (h *Hub) Register(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.clients[c.AccountID] = append(h.clients[c.AccountID], c)
	h.log.Debug().Str("account", c.AccountID).Int("devices", len(h.clients[c.AccountID])).Msg("client registered")
}

// Unregister removes a client from the hub.
func (h *Hub) Unregister(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	list := h.clients[c.AccountID]
	for i, cl := range list {
		if cl == c {
			h.clients[c.AccountID] = append(list[:i], list[i+1:]...)
			break
		}
	}
	if len(h.clients[c.AccountID]) == 0 {
		delete(h.clients, c.AccountID)
	}
	close(c.send)
}

// Send delivers an envelope to all devices of accountID.
// Returns the number of connected devices that received the message.
func (h *Hub) Send(accountID string, env Envelope) int {
	h.mu.RLock()
	clients := h.clients[accountID]
	h.mu.RUnlock()

	delivered := 0
	for _, c := range clients {
		select {
		case c.send <- env:
			delivered++
		default:
			// slow client — skip (client will catch up on reconnect via history)
		}
	}
	return delivered
}

// IsOnline reports whether an account has at least one active connection.
func (h *Hub) IsOnline(accountID string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients[accountID]) > 0
}

// ServeClient runs the read+write loops for a single WebSocket client.
// Blocks until the connection closes.
func (h *Hub) ServeClient(ctx context.Context, c *Client) {
	h.Register(c)
	defer h.Unregister(c)

	// Write loop
	go func() {
		for env := range c.send {
			if err := wsjson.Write(ctx, c.conn, env); err != nil {
				c.conn.Close(websocket.StatusInternalError, "write error")
				return
			}
		}
	}()

	// Read loop — clients send ping/ack frames; server ignores unknown types
	for {
		var msg map[string]any
		if err := wsjson.Read(ctx, c.conn, &msg); err != nil {
			break
		}
		// TODO: handle client-sent events (typing indicators, read receipts)
	}
}

// NewClient creates a Client with a buffered send channel.
func NewClient(accountID string, conn *websocket.Conn) *Client {
	return &Client{
		AccountID: accountID,
		conn:      conn,
		send:      make(chan Envelope, 256),
	}
}
