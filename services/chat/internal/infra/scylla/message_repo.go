// Package scylla implements chat domain repositories backed by ScyllaDB.
package scylla

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/gocql/gocql"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/services/chat/internal/domain"
)

// MessageRepo implements domain.MessageRepository using ScyllaDB.
type MessageRepo struct{ session *gocql.Session }

// NewMessageRepo creates a new ScyllaDB-backed message repository.
func NewMessageRepo(session *gocql.Session) *MessageRepo {
	return &MessageRepo{session: session}
}

// Save persists a message. Takes *domain.Message so the generated
// UUID is written back to the caller's struct (otherwise both the
// HTTP response and the NATS delivery event would carry an empty ID).
func (r *MessageRepo) Save(ctx context.Context, m *domain.Message) error {
	msgID := gocql.TimeUUID()
	m.ID = msgID.String()

	var expiresAt *time.Time = m.ExpiresAt

	q := r.session.Query(
		`INSERT INTO messages
		    (conversation_id, msg_id, sender_id, type, ciphertext, sender_key_data, client_seq, sent_at, reply_to, forwarded_from, pinned)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		m.ConversationID, msgID, m.SenderID, int8(m.Type),
		m.Ciphertext, m.SenderKeyData, m.ClientSeq, m.SentAt, m.ReplyTo, m.ForwardedFrom, false,
	).WithContext(ctx)

	if expiresAt != nil {
		ttl := int(time.Until(*expiresAt).Seconds())
		if ttl > 0 {
			q = r.session.Query(
				`INSERT INTO messages
				    (conversation_id, msg_id, sender_id, type, ciphertext, sender_key_data, client_seq, sent_at, reply_to, forwarded_from, pinned)
				 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				 USING TTL ?`,
				m.ConversationID, msgID, m.SenderID, int8(m.Type),
				m.Ciphertext, m.SenderKeyData, m.ClientSeq, m.SentAt, m.ReplyTo, m.ForwardedFrom, false, ttl,
			).WithContext(ctx)
		}
	}

	if err := q.Exec(); err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "save message", err)
	}
	return nil
}

func (r *MessageRepo) GetHistory(ctx context.Context, conversationID, cursor string, limit int) ([]domain.Message, error) {
	var q *gocql.Query

	if cursor == "" {
		q = r.session.Query(
			`SELECT msg_id, sender_id, type, ciphertext, sender_key_data, client_seq, sent_at, reply_to, edited_at, forwarded_from, pinned
			 FROM messages
			 WHERE conversation_id = ?
			 ORDER BY msg_id DESC
			 LIMIT ?`,
			conversationID, limit,
		).WithContext(ctx)
	} else {
		cursorUUID, err := gocql.ParseUUID(cursor)
		if err != nil {
			return nil, apperrors.New(apperrors.ErrInvalidInput, "invalid cursor")
		}
		q = r.session.Query(
			`SELECT msg_id, sender_id, type, ciphertext, sender_key_data, client_seq, sent_at, reply_to, edited_at, forwarded_from, pinned
			 FROM messages
			 WHERE conversation_id = ? AND msg_id < ?
			 ORDER BY msg_id DESC
			 LIMIT ?`,
			conversationID, cursorUUID, limit,
		).WithContext(ctx)
	}

	scanner := q.Iter().Scanner()
	msgs := make([]domain.Message, 0, limit)

	for scanner.Next() {
		var (
			msgID         gocql.UUID
			senderID      string
			msgType       int8
			ciphertext    []byte
			senderKeyData []byte
			clientSeq     int64
			sentAt        time.Time
			replyTo       *string
			editedAt      *time.Time
			forwardedFrom *string
			pinned        *bool
		)
		if err := scanner.Scan(&msgID, &senderID, &msgType, &ciphertext, &senderKeyData, &clientSeq, &sentAt, &replyTo, &editedAt, &forwardedFrom, &pinned); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan message", err)
		}
		reply := ""
		if replyTo != nil {
			reply = *replyTo
		}
		fwd := ""
		if forwardedFrom != nil {
			fwd = *forwardedFrom
		}
		msgs = append(msgs, domain.Message{
			ID:             msgID.String(),
			ConversationID: conversationID,
			SenderID:       senderID,
			Type:           domain.MessageType(msgType),
			Ciphertext:     ciphertext,
			SenderKeyData:  senderKeyData,
			ClientSeq:      clientSeq,
			SentAt:         sentAt,
			ReplyTo:        reply,
			EditedAt:       editedAt,
			ForwardedFrom:  fwd,
			Pinned:         pinned != nil && *pinned,
		})
	}

	if err := scanner.Err(); err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "get history", err)
	}
	return msgs, nil
}

func (r *MessageRepo) GetByID(ctx context.Context, conversationID, messageID string) (domain.Message, error) {
	msgID, err := gocql.ParseUUID(messageID)
	if err != nil {
		return domain.Message{}, apperrors.New(apperrors.ErrInvalidInput, "invalid message ID")
	}
	var (
		senderID      string
		msgType       int8
		ciphertext    []byte
		senderKeyData []byte
		clientSeq     int64
		sentAt        time.Time
		replyTo       *string
		editedAt      *time.Time
		forwardedFrom *string
		pinned        *bool
	)
	err = r.session.Query(
		`SELECT sender_id, type, ciphertext, sender_key_data, client_seq, sent_at, reply_to, edited_at, forwarded_from, pinned
		 FROM messages WHERE conversation_id = ? AND msg_id = ?`,
		conversationID, msgID,
	).WithContext(ctx).Scan(&senderID, &msgType, &ciphertext, &senderKeyData, &clientSeq, &sentAt, &replyTo, &editedAt, &forwardedFrom, &pinned)
	if err != nil {
		if err == gocql.ErrNotFound {
			return domain.Message{}, apperrors.New(apperrors.ErrNotFound, "message not found")
		}
		return domain.Message{}, apperrors.Wrap(apperrors.ErrInternal, "get message", err)
	}
	reply := ""
	if replyTo != nil {
		reply = *replyTo
	}
	fwd := ""
	if forwardedFrom != nil {
		fwd = *forwardedFrom
	}
	return domain.Message{
		ID:             messageID,
		ConversationID: conversationID,
		SenderID:       senderID,
		Type:           domain.MessageType(msgType),
		Ciphertext:     ciphertext,
		SenderKeyData:  senderKeyData,
		ClientSeq:      clientSeq,
		SentAt:         sentAt,
		ReplyTo:        reply,
		EditedAt:       editedAt,
		ForwardedFrom:  fwd,
		Pinned:         pinned != nil && *pinned,
	}, nil
}

func (r *MessageRepo) SetPinned(ctx context.Context, conversationID, messageID string, pinned bool) error {
	msgID, err := gocql.ParseUUID(messageID)
	if err != nil {
		return apperrors.New(apperrors.ErrInvalidInput, "invalid message ID")
	}
	err = r.session.Query(
		`UPDATE messages SET pinned = ?
		 WHERE conversation_id = ? AND msg_id = ?`,
		pinned, conversationID, msgID,
	).WithContext(ctx).Exec()
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "set pinned", err)
	}
	return nil
}

// ListPinned scans the conversation partition for pinned rows. With ALLOW
// FILTERING this is a partition-scoped scan; for normal-sized chats it's
// fine. If we ever see hot conversations with many messages, a side index
// table would be the next move.
func (r *MessageRepo) ListPinned(ctx context.Context, conversationID string) ([]domain.Message, error) {
	scanner := r.session.Query(
		`SELECT msg_id, sender_id, type, ciphertext, sender_key_data, client_seq, sent_at, reply_to, edited_at, forwarded_from
		 FROM messages
		 WHERE conversation_id = ? AND pinned = true
		 ALLOW FILTERING`,
		conversationID,
	).WithContext(ctx).Iter().Scanner()

	out := make([]domain.Message, 0)
	for scanner.Next() {
		var (
			msgID         gocql.UUID
			senderID      string
			msgType       int8
			ciphertext    []byte
			senderKeyData []byte
			clientSeq     int64
			sentAt        time.Time
			replyTo       *string
			editedAt      *time.Time
			forwardedFrom *string
		)
		if err := scanner.Scan(&msgID, &senderID, &msgType, &ciphertext, &senderKeyData, &clientSeq, &sentAt, &replyTo, &editedAt, &forwardedFrom); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan pinned", err)
		}
		reply := ""
		if replyTo != nil {
			reply = *replyTo
		}
		fwd := ""
		if forwardedFrom != nil {
			fwd = *forwardedFrom
		}
		out = append(out, domain.Message{
			ID:             msgID.String(),
			ConversationID: conversationID,
			SenderID:       senderID,
			Type:           domain.MessageType(msgType),
			Ciphertext:     ciphertext,
			SenderKeyData:  senderKeyData,
			ClientSeq:      clientSeq,
			SentAt:         sentAt,
			ReplyTo:        reply,
			EditedAt:       editedAt,
			ForwardedFrom:  fwd,
			Pinned:         true,
		})
	}
	if err := scanner.Err(); err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "list pinned", err)
	}
	return out, nil
}

func (r *MessageRepo) Edit(ctx context.Context, conversationID, messageID string, ciphertext []byte, editedAt time.Time) error {
	msgID, err := gocql.ParseUUID(messageID)
	if err != nil {
		return apperrors.New(apperrors.ErrInvalidInput, "invalid message ID")
	}
	err = r.session.Query(
		`UPDATE messages SET ciphertext = ?, edited_at = ?
		 WHERE conversation_id = ? AND msg_id = ?`,
		ciphertext, editedAt, conversationID, msgID,
	).WithContext(ctx).Exec()
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "edit message", err)
	}
	return nil
}

func (r *MessageRepo) Delete(ctx context.Context, conversationID, messageID string) error {
	msgID, err := gocql.ParseUUID(messageID)
	if err != nil {
		return apperrors.New(apperrors.ErrInvalidInput, "invalid message ID")
	}

	// Overwrite with tombstone record (type=deleted, empty ciphertext)
	err = r.session.Query(
		`UPDATE messages SET type = ?, ciphertext = ?
		 WHERE conversation_id = ? AND msg_id = ?`,
		int8(domain.MessageTypeDeleted), []byte{}, conversationID, msgID,
	).WithContext(ctx).Exec()
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "delete message", err)
	}
	return nil
}

func (r *MessageRepo) SearchMeta(ctx context.Context, in domain.MessageSearchMetaInput) ([]domain.Message, string, error) {
	limit := in.Limit
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	base := `SELECT msg_id, sender_id, type, ciphertext, sender_key_data, client_seq, sent_at, reply_to, edited_at, forwarded_from, pinned
	 FROM messages WHERE conversation_id = ?`
	args := []any{in.ConversationID}
	if in.Cursor != "" {
		cursorUUID, err := gocql.ParseUUID(in.Cursor)
		if err != nil {
			return nil, "", apperrors.New(apperrors.ErrInvalidInput, "invalid cursor")
		}
		base += ` AND msg_id < ?`
		args = append(args, cursorUUID)
	}
	if in.SenderID != "" {
		base += ` AND sender_id = ?`
		args = append(args, in.SenderID)
	}
	if in.Type != nil {
		base += ` AND type = ?`
		args = append(args, int8(*in.Type))
	}
	if in.From != nil {
		base += ` AND sent_at >= ?`
		args = append(args, *in.From)
	}
	if in.To != nil {
		base += ` AND sent_at <= ?`
		args = append(args, *in.To)
	}
	base += ` ORDER BY msg_id DESC LIMIT ? ALLOW FILTERING`
	args = append(args, limit+1)

	scanner := r.session.Query(base, args...).WithContext(ctx).Iter().Scanner()
	out := make([]domain.Message, 0, limit+1)
	for scanner.Next() {
		var (
			msgID         gocql.UUID
			senderID      string
			msgType       int8
			ciphertext    []byte
			senderKeyData []byte
			clientSeq     int64
			sentAt        time.Time
			replyTo       *string
			editedAt      *time.Time
			forwardedFrom *string
			pinned        *bool
		)
		if err := scanner.Scan(&msgID, &senderID, &msgType, &ciphertext, &senderKeyData, &clientSeq, &sentAt, &replyTo, &editedAt, &forwardedFrom, &pinned); err != nil {
			return nil, "", apperrors.Wrap(apperrors.ErrInternal, "scan searched message", err)
		}
		reply := ""
		if replyTo != nil {
			reply = *replyTo
		}
		fwd := ""
		if forwardedFrom != nil {
			fwd = *forwardedFrom
		}
		out = append(out, domain.Message{
			ID:             msgID.String(),
			ConversationID: in.ConversationID,
			SenderID:       senderID,
			Type:           domain.MessageType(msgType),
			Ciphertext:     ciphertext,
			SenderKeyData:  senderKeyData,
			ClientSeq:      clientSeq,
			SentAt:         sentAt,
			ReplyTo:        reply,
			EditedAt:       editedAt,
			ForwardedFrom:  fwd,
			Pinned:         pinned != nil && *pinned,
		})
	}
	if err := scanner.Err(); err != nil {
		return nil, "", apperrors.Wrap(apperrors.ErrInternal, "search message meta", err)
	}
	next := ""
	if len(out) > limit {
		next = out[limit].ID
		out = out[:limit]
	}
	return out, next, nil
}

// ─── ConversationRepo ─────────────────────────────────────────────────────────

// ConversationRepo implements domain.ConversationRepository.
type ConversationRepo struct{ session *gocql.Session }

func NewConversationRepo(session *gocql.Session) *ConversationRepo {
	return &ConversationRepo{session: session}
}

func (r *ConversationRepo) Create(ctx context.Context, c *domain.Conversation) error {
	convID := gocql.TimeUUID()
	c.ID = convID.String()

	batch := r.session.NewBatch(gocql.LoggedBatch).WithContext(ctx)
	batch.Entries = append(batch.Entries, gocql.BatchEntry{
		Stmt: `INSERT INTO conversations (id, type, name, member_ids, created_at) VALUES (?, ?, ?, ?, ?)`,
		Args: []interface{}{convID, int8(c.Type), c.Name, c.MemberIDs, c.CreatedAt},
	})
	for _, memberID := range c.MemberIDs {
		batch.Entries = append(batch.Entries, gocql.BatchEntry{
			Stmt: `INSERT INTO member_conversations (member_id, conv_id, created_at) VALUES (?, ?, ?)`,
			Args: []interface{}{memberID, convID, c.CreatedAt},
		})
	}

	if err := r.session.ExecuteBatch(batch); err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "create conversation", err)
	}
	return nil
}

func (r *ConversationRepo) Get(ctx context.Context, id string) (domain.Conversation, error) {
	convID, err := gocql.ParseUUID(id)
	if err != nil {
		return domain.Conversation{}, apperrors.New(apperrors.ErrInvalidInput, "invalid conversation ID")
	}

	var c domain.Conversation
	var convType int8
	var name *string // ScyllaDB returns nil for missing column on rows pre-migration.
	err = r.session.Query(
		`SELECT id, type, name, member_ids, created_at, last_msg_id, last_msg_at
		 FROM conversations WHERE id = ?`, convID,
	).WithContext(ctx).Scan(
		&c.ID, &convType, &name, &c.MemberIDs, &c.CreatedAt, &c.LastMsgID, &c.LastMsgAt,
	)
	if err != nil {
		if err == gocql.ErrNotFound {
			return domain.Conversation{}, apperrors.New(apperrors.ErrNotFound, "conversation not found")
		}
		return domain.Conversation{}, apperrors.Wrap(apperrors.ErrInternal, "get conversation", err)
	}
	c.Type = domain.ConversationType(convType)
	if name != nil {
		c.Name = *name
	}
	return c, nil
}

func (r *ConversationRepo) GetForMember(ctx context.Context, memberID string) ([]string, error) {
	scanner := r.session.Query(
		`SELECT conv_id FROM member_conversations WHERE member_id = ?`, memberID,
	).WithContext(ctx).Iter().Scanner()

	var ids []string
	for scanner.Next() {
		var convID gocql.UUID
		if err := scanner.Scan(&convID); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan conversation id", err)
		}
		ids = append(ids, convID.String())
	}

	if err := scanner.Err(); err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "get conversations for member", err)
	}
	return ids, nil
}

// FindDirectBetween scans memberA's conversations and returns the conv_id of
// the (single) direct chat that also has memberB as the other party.
// Empty string if none. Used to make Create idempotent for direct chats so
// repeated «Открыть чат» clicks don't keep producing duplicates.
func (r *ConversationRepo) FindDirectBetween(ctx context.Context, memberA, memberB string) (string, error) {
	convIDs, err := r.GetForMember(ctx, memberA)
	if err != nil {
		return "", err
	}
	for _, id := range convIDs {
		conv, err := r.Get(ctx, id)
		if err != nil {
			// Skip orphan or transient errors — they shouldn't block lookup.
			continue
		}
		if conv.Type != domain.ConversationTypeDirect {
			continue
		}
		if len(conv.MemberIDs) != 2 {
			continue
		}
		// Both members must be (memberA, memberB) in any order.
		if (conv.MemberIDs[0] == memberA && conv.MemberIDs[1] == memberB) ||
			(conv.MemberIDs[0] == memberB && conv.MemberIDs[1] == memberA) {
			return conv.ID, nil
		}
	}
	return "", nil
}

func (r *ConversationRepo) UpdateLastMessage(ctx context.Context, conversationID, msgID string, at time.Time) error {
	convID, err := gocql.ParseUUID(conversationID)
	if err != nil {
		return apperrors.New(apperrors.ErrInvalidInput, "invalid conversation ID")
	}

	err = r.session.Query(
		`UPDATE conversations SET last_msg_id = ?, last_msg_at = ? WHERE id = ?`,
		msgID, at, convID,
	).WithContext(ctx).Exec()
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "update last message", err)
	}
	return nil
}

func (r *ConversationRepo) SearchForMember(ctx context.Context, memberID, query string, limit, offset int) ([]domain.Conversation, int, error) {
	ids, err := r.GetForMember(ctx, memberID)
	if err != nil {
		return nil, -1, err
	}
	q := strings.ToLower(strings.TrimSpace(query))
	if q == "" {
		return []domain.Conversation{}, -1, nil
	}
	filtered := make([]domain.Conversation, 0, len(ids))
	for _, id := range ids {
		conv, err := r.Get(ctx, id)
		if err != nil {
			continue
		}
		target := strings.ToLower(strings.TrimSpace(conv.Name))
		if conv.Type == domain.ConversationTypeDirect {
			for _, member := range conv.MemberIDs {
				if member != memberID {
					target = strings.ToLower(member)
					break
				}
			}
		}
		if strings.Contains(target, q) {
			filtered = append(filtered, conv)
		}
	}
	if offset >= len(filtered) {
		return []domain.Conversation{}, -1, nil
	}
	end := offset + limit
	if end > len(filtered) {
		end = len(filtered)
	}
	next := -1
	if end < len(filtered) {
		next = end
	}
	return filtered[offset:end], next, nil
}

// ─── ReactionRepo ────────────────────────────────────────────────────────────

type ReactionRepo struct{ session *gocql.Session }

func NewReactionRepo(session *gocql.Session) *ReactionRepo {
	return &ReactionRepo{session: session}
}

func (r *ReactionRepo) Add(ctx context.Context, x domain.Reaction) error {
	msgID, err := gocql.ParseUUID(x.MessageID)
	if err != nil {
		return apperrors.New(apperrors.ErrInvalidInput, "invalid message ID")
	}
	err = r.session.Query(
		`INSERT INTO message_reactions
		    (conversation_id, msg_id, actor_id, emoji, reacted_at)
		 VALUES (?, ?, ?, ?, ?)`,
		x.ConversationID, msgID, x.ActorID, x.Emoji, x.ReactedAt,
	).WithContext(ctx).Exec()
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "add reaction", err)
	}
	return nil
}

func (r *ReactionRepo) Remove(ctx context.Context, conversationID, messageID, actorID, emoji string) error {
	msgID, err := gocql.ParseUUID(messageID)
	if err != nil {
		return apperrors.New(apperrors.ErrInvalidInput, "invalid message ID")
	}
	err = r.session.Query(
		`DELETE FROM message_reactions
		 WHERE conversation_id = ? AND msg_id = ? AND actor_id = ? AND emoji = ?`,
		conversationID, msgID, actorID, emoji,
	).WithContext(ctx).Exec()
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "remove reaction", err)
	}
	return nil
}

func (r *ReactionRepo) ListForMessage(ctx context.Context, conversationID, messageID string) ([]domain.Reaction, error) {
	msgID, err := gocql.ParseUUID(messageID)
	if err != nil {
		return nil, apperrors.New(apperrors.ErrInvalidInput, "invalid message ID")
	}
	scanner := r.session.Query(
		`SELECT actor_id, emoji, reacted_at FROM message_reactions
		 WHERE conversation_id = ? AND msg_id = ?`,
		conversationID, msgID,
	).WithContext(ctx).Iter().Scanner()

	out := make([]domain.Reaction, 0)
	for scanner.Next() {
		var r domain.Reaction
		r.ConversationID = conversationID
		r.MessageID = messageID
		if err := scanner.Scan(&r.ActorID, &r.Emoji, &r.ReactedAt); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan reaction", err)
		}
		out = append(out, r)
	}
	if err := scanner.Err(); err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "list reactions", err)
	}
	return out, nil
}

// ─── ScyllaDB session factory ─────────────────────────────────────────────────

// NewSession creates a shard-aware ScyllaDB session.
func NewSession(hosts []string, keyspace string) (*gocql.Session, error) {
	cluster := gocql.NewCluster(hosts...)
	cluster.Keyspace = keyspace
	cluster.Consistency = gocql.LocalQuorum
	cluster.ProtoVersion = 4
	cluster.ConnectTimeout = 10 * time.Second
	cluster.Timeout = 5 * time.Second

	// Token-aware routing is critical for Scylla performance
	cluster.PoolConfig.HostSelectionPolicy = gocql.TokenAwareHostPolicy(
		gocql.RoundRobinHostPolicy(),
	)

	session, err := cluster.CreateSession()
	if err != nil {
		return nil, fmt.Errorf("scylla connect: %w", err)
	}
	return session, nil
}
