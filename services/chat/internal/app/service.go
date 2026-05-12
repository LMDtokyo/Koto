// Package app contains chat use-cases.
package app

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"

	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/services/chat/internal/domain"
)

// EventPublisher publishes delivery events to the message broker.
type EventPublisher interface {
	Publish(ctx context.Context, subject string, data []byte) error
}

// Service orchestrates chat use-cases.
type Service struct {
	messages      domain.MessageRepository
	conversations domain.ConversationRepository
	reactions     domain.ReactionRepository
	publisher     EventPublisher
}

// New creates the chat application service.
func New(
	messages domain.MessageRepository,
	conversations domain.ConversationRepository,
	reactions domain.ReactionRepository,
	publisher EventPublisher,
) *Service {
	return &Service{
		messages:      messages,
		conversations: conversations,
		reactions:     reactions,
		publisher:     publisher,
	}
}

// SendInput is the payload for sending a message.
type SendInput struct {
	ConversationID string
	SenderID       string
	Type           domain.MessageType
	Ciphertext     []byte
	SenderKeyData  []byte // sealed sender envelope
	ClientSeq      int64
	ExpiresAt      *time.Time
	ReplyTo        string // optional — id of the message being quoted
	ForwardedFrom  string // optional — original author's account id when forwarding
}

// SendMessage persists a message and emits a delivery event.
func (s *Service) SendMessage(ctx context.Context, in SendInput) (domain.Message, error) {
	if len(in.Ciphertext) == 0 {
		return domain.Message{}, apperrors.New(apperrors.ErrInvalidInput, "ciphertext is required")
	}
	if in.ConversationID == "" || in.SenderID == "" {
		return domain.Message{}, apperrors.New(apperrors.ErrInvalidInput, "conversation_id and sender_id are required")
	}

	conv, err := s.conversations.Get(ctx, in.ConversationID)
	if err != nil {
		return domain.Message{}, err
	}

	now := time.Now().UTC()
	msg := domain.Message{
		ConversationID: in.ConversationID,
		SenderID:       in.SenderID,
		Type:           in.Type,
		Ciphertext:     in.Ciphertext,
		SenderKeyData:  in.SenderKeyData,
		ClientSeq:      in.ClientSeq,
		SentAt:         now,
		ExpiresAt:      in.ExpiresAt,
		ReplyTo:        in.ReplyTo,
		ForwardedFrom:  in.ForwardedFrom,
	}

	if err := s.messages.Save(ctx, &msg); err != nil {
		return domain.Message{}, err
	}
	// msg.ID is now set by the repo (TimeUUID generated inside Save).

	if err := s.conversations.UpdateLastMessage(ctx, conv.ID, msg.ID, now); err != nil {
		// non-fatal — delivery succeeds, metadata is eventually consistent
		fmt.Printf("warn: update last message conv=%s err=%v\n", conv.ID, err)
	}

	// Collect recipient IDs (everyone except the sender)
	recipients := make([]string, 0, len(conv.MemberIDs))
	for _, id := range conv.MemberIDs {
		if id != in.SenderID {
			recipients = append(recipients, id)
		}
	}

	event := domain.DeliveryEvent{
		MessageID:      msg.ID,
		ConversationID: conv.ID,
		SenderID:       in.SenderID,
		RecipientIDs:   recipients,
		Type:           in.Type,
		Ciphertext:     msg.Ciphertext,
		SentAt:         now,
		ReplyTo:        msg.ReplyTo,
		ForwardedFrom:  msg.ForwardedFrom,
	}
	payload, _ := json.Marshal(event)
	subject := fmt.Sprintf("chat.deliver.%s", in.ConversationID)
	_ = s.publisher.Publish(ctx, subject, payload) // delivery is best-effort

	return msg, nil
}

// GetHistory returns paginated message history.
func (s *Service) GetHistory(ctx context.Context, conversationID, senderID, cursor string, limit int) ([]domain.Message, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}

	// Verify sender is a member
	conv, err := s.conversations.Get(ctx, conversationID)
	if err != nil {
		return nil, err
	}

	isMember := false
	for _, id := range conv.MemberIDs {
		if id == senderID {
			isMember = true
			break
		}
	}
	if !isMember {
		return nil, apperrors.New(apperrors.ErrForbidden, "not a member of this conversation")
	}

	return s.messages.GetHistory(ctx, conversationID, cursor, limit)
}

// CreateConversation creates a new conversation between members. If name is
// non-empty it's used as the group's display label; ignored for direct chats.
func (s *Service) CreateConversation(
	ctx context.Context,
	creatorID string,
	memberIDs []string,
	convType domain.ConversationType,
	name string,
) (domain.Conversation, error) {
	// Ensure creator is in the member list
	hasCreator := false
	for _, id := range memberIDs {
		if id == creatorID {
			hasCreator = true
			break
		}
	}
	if !hasCreator {
		memberIDs = append(memberIDs, creatorID)
	}

	if len(memberIDs) < 2 {
		return domain.Conversation{}, apperrors.New(apperrors.ErrInvalidInput, "conversation requires at least 2 members")
	}

	if convType == domain.ConversationTypeDirect && len(memberIDs) != 2 {
		return domain.Conversation{}, apperrors.New(apperrors.ErrInvalidInput, "direct conversation must have exactly 2 members")
	}

	// Direct conversations don't carry a user-facing label — recipient renders
	// the peer's profile instead. Strip any name the client may have sent so
	// the storage stays clean.
	if convType == domain.ConversationTypeDirect {
		name = ""

		// Idempotency: если direct-чат между этими двумя уже существует —
		// возвращаем его, не создаём дубликат. Любое количество кликов
		// «Открыть чат» / «Написать» на одного и того же peer'а отдаёт
		// один и тот же conv_id.
		other := memberIDs[0]
		if other == creatorID {
			other = memberIDs[1]
		}
		if existingID, err := s.conversations.FindDirectBetween(ctx, creatorID, other); err == nil && existingID != "" {
			existing, err := s.conversations.Get(ctx, existingID)
			if err == nil {
				return existing, nil
			}
		}
	}
	if len(name) > 64 {
		return domain.Conversation{}, apperrors.New(apperrors.ErrInvalidInput, "name too long (max 64)")
	}

	conv := domain.Conversation{
		Type:      convType,
		Name:      name,
		MemberIDs: memberIDs,
		CreatedAt: time.Now().UTC(),
	}

	if err := s.conversations.Create(ctx, &conv); err != nil {
		return domain.Conversation{}, err
	}

	// Notify everyone except the creator that they have a new conversation.
	// Best-effort: a delivery failure here shouldn't fail the create call —
	// recipients still discover the chat on their next sync.
	recipients := make([]string, 0, len(conv.MemberIDs))
	for _, id := range conv.MemberIDs {
		if id != creatorID {
			recipients = append(recipients, id)
		}
	}
	if len(recipients) > 0 {
		event := domain.ConversationCreatedEvent{
			ConversationID: conv.ID,
			Type:           conv.Type,
			Name:           conv.Name,
			CreatorID:      creatorID,
			MemberIDs:      conv.MemberIDs,
			RecipientIDs:   recipients,
			CreatedAt:      conv.CreatedAt,
		}
		payload, _ := json.Marshal(event)
		subject := fmt.Sprintf("chat.conversation.created.%s", conv.ID)
		_ = s.publisher.Publish(ctx, subject, payload)
	}

	return conv, nil
}

// SetPinned flips the pinned flag for a message. Caller must be a member of
// the conversation. Publishes a NATS event so peers update their UI live.
func (s *Service) SetPinned(ctx context.Context, conversationID, messageID, actorID string, pinned bool) error {
	if conversationID == "" || messageID == "" || actorID == "" {
		return apperrors.New(apperrors.ErrInvalidInput, "conversation_id, message_id and actor_id required")
	}
	conv, err := s.conversations.Get(ctx, conversationID)
	if err != nil {
		return err
	}
	isMember := false
	for _, id := range conv.MemberIDs {
		if id == actorID {
			isMember = true
			break
		}
	}
	if !isMember {
		return apperrors.New(apperrors.ErrForbidden, "not a member of this conversation")
	}
	if err := s.messages.SetPinned(ctx, conversationID, messageID, pinned); err != nil {
		return err
	}

	recipients := make([]string, 0, len(conv.MemberIDs))
	for _, id := range conv.MemberIDs {
		if id != actorID {
			recipients = append(recipients, id)
		}
	}
	if len(recipients) > 0 {
		event := domain.PinEvent{
			ConversationID: conversationID,
			MessageID:      messageID,
			ActorID:        actorID,
			Pinned:         pinned,
			RecipientIDs:   recipients,
			At:             time.Now().UTC(),
		}
		payload, _ := json.Marshal(event)
		subject := fmt.Sprintf("chat.pin.%s", conversationID)
		_ = s.publisher.Publish(ctx, subject, payload)
	}
	return nil
}

// ListPinned returns every pinned message in the conversation. Caller must
// be a member.
func (s *Service) ListPinned(ctx context.Context, conversationID, requesterID string) ([]domain.Message, error) {
	conv, err := s.conversations.Get(ctx, conversationID)
	if err != nil {
		return nil, err
	}
	isMember := false
	for _, id := range conv.MemberIDs {
		if id == requesterID {
			isMember = true
			break
		}
	}
	if !isMember {
		return nil, apperrors.New(apperrors.ErrForbidden, "not a member of this conversation")
	}
	return s.messages.ListPinned(ctx, conversationID)
}

// EditMessage replaces the ciphertext of a message. Only the original sender
// can edit; the receiver decrypts the new ciphertext under the existing
// Signal session and overwrites their cached plaintext for the same id.
func (s *Service) EditMessage(
	ctx context.Context,
	conversationID, messageID, requesterID string,
	newCiphertext []byte,
) (time.Time, error) {
	if conversationID == "" || messageID == "" || requesterID == "" {
		return time.Time{}, apperrors.New(apperrors.ErrInvalidInput, "conversation_id, message_id and sender_id are required")
	}
	if len(newCiphertext) == 0 {
		return time.Time{}, apperrors.New(apperrors.ErrInvalidInput, "ciphertext is required")
	}

	conv, err := s.conversations.Get(ctx, conversationID)
	if err != nil {
		return time.Time{}, err
	}
	original, err := s.messages.GetByID(ctx, conversationID, messageID)
	if err != nil {
		return time.Time{}, err
	}
	if original.SenderID != requesterID {
		return time.Time{}, apperrors.New(apperrors.ErrForbidden, "only the original sender can edit a message")
	}

	now := time.Now().UTC()
	if err := s.messages.Edit(ctx, conversationID, messageID, newCiphertext, now); err != nil {
		return time.Time{}, err
	}

	recipients := make([]string, 0, len(conv.MemberIDs))
	for _, id := range conv.MemberIDs {
		if id != requesterID {
			recipients = append(recipients, id)
		}
	}
	if len(recipients) > 0 {
		event := domain.EditEvent{
			MessageID:      messageID,
			ConversationID: conversationID,
			SenderID:       requesterID,
			RecipientIDs:   recipients,
			Ciphertext:     newCiphertext,
			EditedAt:       now,
		}
		payload, _ := json.Marshal(event)
		subject := fmt.Sprintf("chat.edit.%s", conversationID)
		_ = s.publisher.Publish(ctx, subject, payload)
	}

	return now, nil
}

// DeleteMessage marks a message as deleted. Only the original sender may delete it.
func (s *Service) DeleteMessage(ctx context.Context, conversationID, messageID, senderID string) error {
	if conversationID == "" || messageID == "" || senderID == "" {
		return apperrors.New(apperrors.ErrInvalidInput, "conversation_id, message_id and sender_id are required")
	}

	// Verify the requester is a member of the conversation
	conv, err := s.conversations.Get(ctx, conversationID)
	if err != nil {
		return err
	}

	isMember := false
	for _, id := range conv.MemberIDs {
		if id == senderID {
			isMember = true
			break
		}
	}
	if !isMember {
		return apperrors.New(apperrors.ErrForbidden, "not a member of this conversation")
	}

	return s.messages.Delete(ctx, conversationID, messageID)
}

// ToggleReaction adds [emoji] from [actorID] on a message, or removes it if
// the same actor+emoji pair already exists. Returns true when the result is
// "added", false when "removed" — useful for clients that want to update
// optimistic UI without re-fetching.
func (s *Service) ToggleReaction(ctx context.Context, conversationID, messageID, actorID, emoji string) (bool, error) {
	if conversationID == "" || messageID == "" || actorID == "" {
		return false, apperrors.New(apperrors.ErrInvalidInput, "conversation_id, message_id and actor_id are required")
	}
	if emoji == "" || len(emoji) > 32 {
		return false, apperrors.New(apperrors.ErrInvalidInput, "invalid emoji")
	}

	conv, err := s.conversations.Get(ctx, conversationID)
	if err != nil {
		return false, err
	}
	isMember := false
	for _, id := range conv.MemberIDs {
		if id == actorID {
			isMember = true
			break
		}
	}
	if !isMember {
		return false, apperrors.New(apperrors.ErrForbidden, "not a member of this conversation")
	}

	// Toggle: if any row exists with same actor+emoji on this msg, remove it.
	existing, err := s.reactions.ListForMessage(ctx, conversationID, messageID)
	if err != nil {
		return false, err
	}
	already := false
	for _, r := range existing {
		if r.ActorID == actorID && r.Emoji == emoji {
			already = true
			break
		}
	}

	now := time.Now().UTC()
	if already {
		if err := s.reactions.Remove(ctx, conversationID, messageID, actorID, emoji); err != nil {
			return false, err
		}
	} else {
		if err := s.reactions.Add(ctx, domain.Reaction{
			ConversationID: conversationID,
			MessageID:      messageID,
			ActorID:        actorID,
			Emoji:          emoji,
			ReactedAt:      now,
		}); err != nil {
			return false, err
		}
	}

	// Publish to NATS for fanout. Recipients = everyone except the actor.
	recipients := make([]string, 0, len(conv.MemberIDs))
	for _, id := range conv.MemberIDs {
		if id != actorID {
			recipients = append(recipients, id)
		}
	}
	if len(recipients) > 0 {
		event := domain.ReactionEvent{
			ConversationID: conversationID,
			MessageID:      messageID,
			ActorID:        actorID,
			Emoji:          emoji,
			Added:          !already,
			RecipientIDs:   recipients,
			ReactedAt:      now,
		}
		payload, _ := json.Marshal(event)
		subject := fmt.Sprintf("chat.reaction.%s", conversationID)
		_ = s.publisher.Publish(ctx, subject, payload)
	}

	return !already, nil
}

// ListReactions returns all reactions on a message. Caller must be a member
// of the conversation.
func (s *Service) ListReactions(ctx context.Context, conversationID, messageID, requesterID string) ([]domain.Reaction, error) {
	conv, err := s.conversations.Get(ctx, conversationID)
	if err != nil {
		return nil, err
	}
	isMember := false
	for _, id := range conv.MemberIDs {
		if id == requesterID {
			isMember = true
			break
		}
	}
	if !isMember {
		return nil, apperrors.New(apperrors.ErrForbidden, "not a member of this conversation")
	}
	return s.reactions.ListForMessage(ctx, conversationID, messageID)
}

// GetConversations returns all conversations for a member.
func (s *Service) GetConversations(ctx context.Context, memberID string) ([]domain.Conversation, error) {
	ids, err := s.conversations.GetForMember(ctx, memberID)
	if err != nil {
		return nil, err
	}

	convs := make([]domain.Conversation, 0, len(ids))
	for _, id := range ids {
		conv, err := s.conversations.Get(ctx, id)
		if err != nil {
			continue // skip unavailable conversations
		}
		convs = append(convs, conv)
	}

	return convs, nil
}

type SearchConversationsInput struct {
	MemberID string
	Query    string
	Limit    int
	Cursor   string // offset
}

type SearchConversationsOutput struct {
	Items      []domain.Conversation
	NextCursor string
	HasMore    bool
}

func (s *Service) SearchConversations(ctx context.Context, in SearchConversationsInput) (SearchConversationsOutput, error) {
	q := strings.TrimSpace(strings.ToLower(in.Query))
	if len(q) < 2 {
		return SearchConversationsOutput{}, apperrors.New(apperrors.ErrInvalidInput, "query must be at least 2 characters")
	}
	limit := in.Limit
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	offset := 0
	if in.Cursor != "" {
		n, err := strconv.Atoi(in.Cursor)
		if err != nil || n < 0 {
			return SearchConversationsOutput{}, apperrors.New(apperrors.ErrInvalidInput, "invalid cursor")
		}
		offset = n
	}

	items, next, err := s.conversations.SearchForMember(ctx, in.MemberID, q, limit, offset)
	if err != nil {
		return SearchConversationsOutput{}, err
	}
	out := SearchConversationsOutput{Items: items}
	if next >= 0 {
		out.HasMore = true
		out.NextCursor = strconv.Itoa(next)
	}
	return out, nil
}

type SearchMessagesMetaInput struct {
	ConversationID string
	RequesterID    string
	SenderID       string
	From           *time.Time
	To             *time.Time
	Type           *domain.MessageType
	Limit          int
	Cursor         string
}

type SearchMessagesMetaOutput struct {
	Items      []domain.Message
	NextCursor string
}

func (s *Service) SearchMessagesMeta(ctx context.Context, in SearchMessagesMetaInput) (SearchMessagesMetaOutput, error) {
	conv, err := s.conversations.Get(ctx, in.ConversationID)
	if err != nil {
		return SearchMessagesMetaOutput{}, err
	}
	isMember := false
	for _, id := range conv.MemberIDs {
		if id == in.RequesterID {
			isMember = true
			break
		}
	}
	if !isMember {
		return SearchMessagesMetaOutput{}, apperrors.New(apperrors.ErrForbidden, "not a member of this conversation")
	}
	limit := in.Limit
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	items, nextCursor, err := s.messages.SearchMeta(ctx, domain.MessageSearchMetaInput{
		ConversationID: in.ConversationID,
		SenderID:       in.SenderID,
		From:           in.From,
		To:             in.To,
		Type:           in.Type,
		Limit:          limit,
		Cursor:         in.Cursor,
	})
	if err != nil {
		return SearchMessagesMetaOutput{}, err
	}
	return SearchMessagesMetaOutput{Items: items, NextCursor: nextCursor}, nil
}
