// Package app contains user management use-cases.
package app

import (
	"context"
	"regexp"
	"strconv"
	"strings"
	"time"

	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/services/user/internal/domain"
)

// Telegram-style: 5–32 chars, [a-z0-9_], must start with a letter and end
// with a letter or digit (no trailing underscore). Stored lower-cased;
// matching is case-insensitive at the DB layer.
var usernameRegex = regexp.MustCompile(`^[a-z][a-z0-9_]{3,30}[a-z0-9]$`)

// Reserved handles that should never be claimable — admin / brand names
// plus first-class app pages, so phishing handles can't appear.
var reservedUsernames = map[string]struct{}{
	"admin": {}, "administrator": {}, "support": {}, "help": {},
	"koto": {}, "official": {}, "system": {}, "moderator": {}, "mod": {},
	"root": {}, "security": {}, "abuse": {}, "noreply": {}, "no_reply": {},
	"team": {}, "staff": {}, "info": {}, "contact": {}, "settings": {},
	"me": {}, "self": {},
}

// Service orchestrates user use-cases.
type Service struct {
	profiles domain.ProfileRepository
	contacts domain.ContactRepository
	prekeys  domain.PrekeyRepository
}

func New(profiles domain.ProfileRepository, contacts domain.ContactRepository, prekeys domain.PrekeyRepository) *Service {
	return &Service{profiles: profiles, contacts: contacts, prekeys: prekeys}
}

// UpdateProfileInput carries fields the user may set or update.
type UpdateProfileInput struct {
	AccountID   string
	DisplayName string
	AvatarURL   string
	BannerURL   string
	Bio         string
}

// UpdateProfile creates or updates a user profile.
func (s *Service) UpdateProfile(ctx context.Context, in UpdateProfileInput) (domain.Profile, error) {
	if len(in.DisplayName) > 64 {
		return domain.Profile{}, apperrors.New(apperrors.ErrInvalidInput, "display_name too long (max 64)")
	}
	if len(in.Bio) > 300 {
		return domain.Profile{}, apperrors.New(apperrors.ErrInvalidInput, "bio too long (max 300)")
	}
	// avatar_url / banner_url come from the media service as opaque file ids
	// (or fully-qualified URLs in dev). Cap length so a malicious client can't
	// stuff an MB-sized blob into the column.
	if len(in.AvatarURL) > 512 {
		return domain.Profile{}, apperrors.New(apperrors.ErrInvalidInput, "avatar_url too long")
	}
	if len(in.BannerURL) > 512 {
		return domain.Profile{}, apperrors.New(apperrors.ErrInvalidInput, "banner_url too long")
	}

	p := domain.Profile{
		AccountID:   in.AccountID,
		DisplayName: in.DisplayName,
		AvatarURL:   in.AvatarURL,
		BannerURL:   in.BannerURL,
		Bio:         in.Bio,
		UpdatedAt:   time.Now().UTC(),
	}

	if err := s.profiles.Upsert(ctx, p); err != nil {
		return domain.Profile{}, err
	}
	return p, nil
}

// GetProfile retrieves a user profile by account ID.
func (s *Service) GetProfile(ctx context.Context, accountID string) (domain.Profile, error) {
	return s.profiles.Get(ctx, accountID)
}

// GetProfiles retrieves multiple profiles in one round-trip.
func (s *Service) GetProfiles(ctx context.Context, accountIDs []string) ([]domain.Profile, error) {
	if len(accountIDs) > 200 {
		return nil, apperrors.New(apperrors.ErrInvalidInput, "max 200 profiles per request")
	}
	return s.profiles.GetBatch(ctx, accountIDs)
}

// UsernameAvailability is what the client gets when probing a candidate
// handle: structured so the UI can colour the input correctly per state.
type UsernameAvailability struct {
	Available bool   `json:"available"`
	Valid     bool   `json:"valid"`
	Reason    string `json:"reason,omitempty"`
}

// CheckUsername reports whether [candidate] is a syntactically valid handle
// AND not already taken by another account. The check is case-insensitive.
// `byAccountID` is treated as "self-reuse" — re-typing your own current
// username comes back as available so the save button stays enabled.
func (s *Service) CheckUsername(ctx context.Context, candidate, byAccountID string) (UsernameAvailability, error) {
	candidate = strings.ToLower(strings.TrimSpace(candidate))
	if reason := validateUsername(candidate); reason != "" {
		return UsernameAvailability{Valid: false, Available: false, Reason: reason}, nil
	}
	taken, err := s.profiles.IsUsernameTaken(ctx, candidate, byAccountID)
	if err != nil {
		return UsernameAvailability{}, err
	}
	if taken {
		return UsernameAvailability{Valid: true, Available: false, Reason: "taken"}, nil
	}
	return UsernameAvailability{Valid: true, Available: true}, nil
}

// FindByUsername resolves a public @handle to a profile so the New Chat
// flow can turn it into an account_id. Strips the leading @ if present.
func (s *Service) FindByUsername(ctx context.Context, username string) (domain.Profile, error) {
	candidate := strings.TrimSpace(strings.TrimPrefix(strings.ToLower(username), "@"))
	if reason := validateUsername(candidate); reason != "" {
		return domain.Profile{}, apperrors.New(apperrors.ErrNotFound, reason)
	}
	return s.profiles.FindByUsername(ctx, candidate)
}

// SetUsername persists a new username for the caller after validating format
// and uniqueness. The unique constraint at the DB layer is the source of
// truth — even a TOCTOU race between availability check and write surfaces
// here as ErrAlreadyExists.
func (s *Service) SetUsername(ctx context.Context, accountID, username string) (string, error) {
	username = strings.ToLower(strings.TrimSpace(username))
	if reason := validateUsername(username); reason != "" {
		return "", apperrors.New(apperrors.ErrInvalidInput, reason)
	}
	if err := s.profiles.SetUsername(ctx, accountID, username); err != nil {
		return "", err
	}
	return username, nil
}

type SearchProfilesInput struct {
	Query  string
	Limit  int
	Cursor string // decimal offset
}

type SearchProfilesOutput struct {
	Profiles    []domain.Profile
	NextCursor  string
	HasMore     bool
}

// SearchProfiles finds public profiles by username/display-name.
func (s *Service) SearchProfiles(ctx context.Context, in SearchProfilesInput) (SearchProfilesOutput, error) {
	q := strings.TrimSpace(strings.ToLower(in.Query))
	if len(q) < 2 {
		return SearchProfilesOutput{}, apperrors.New(apperrors.ErrInvalidInput, "query must be at least 2 characters")
	}
	limit := in.Limit
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	offset := 0
	if in.Cursor != "" {
		n, err := strconv.Atoi(in.Cursor)
		if err != nil || n < 0 {
			return SearchProfilesOutput{}, apperrors.New(apperrors.ErrInvalidInput, "invalid cursor")
		}
		offset = n
	}

	profiles, next, err := s.profiles.Search(ctx, q, limit, offset)
	if err != nil {
		return SearchProfilesOutput{}, err
	}
	out := SearchProfilesOutput{Profiles: profiles}
	if next >= 0 {
		out.HasMore = true
		out.NextCursor = strconv.Itoa(next)
	}
	return out, nil
}

// validateUsername returns an empty string when the candidate matches the
// public format rules (5–32 chars, [a-z0-9_], starts with a letter, ends
// with letter or digit, not reserved). Otherwise returns a human-readable
// reason suitable for display in the UI.
func validateUsername(s string) string {
	switch {
	case len(s) == 0:
		return "username required"
	case len(s) < 5:
		return "too short — min 5 characters"
	case len(s) > 32:
		return "too long — max 32 characters"
	case !usernameRegex.MatchString(s):
		return "must start with a letter and use only a–z, 0–9, _"
	}
	if _, reserved := reservedUsernames[s]; reserved {
		return "reserved"
	}
	return ""
}

// AddContact adds accountID to the requester's contact list.
func (s *Service) AddContact(ctx context.Context, ownerID, contactID, nickname string) error {
	if ownerID == contactID {
		return apperrors.New(apperrors.ErrInvalidInput, "cannot add yourself as a contact")
	}
	return s.contacts.Add(ctx, domain.Contact{
		OwnerID:   ownerID,
		ContactID: contactID,
		Nickname:  nickname,
		AddedAt:   time.Now().UTC(),
	})
}

// RemoveContact removes accountID from the requester's contacts.
func (s *Service) RemoveContact(ctx context.Context, ownerID, contactID string) error {
	return s.contacts.Remove(ctx, ownerID, contactID)
}

// ListContacts returns all contacts for the requesting account.
func (s *Service) ListContacts(ctx context.Context, ownerID string) ([]domain.Contact, error) {
	return s.contacts.List(ctx, ownerID)
}

// BlockContact marks a contact as blocked.
func (s *Service) BlockContact(ctx context.Context, ownerID, contactID string) error {
	if ownerID == contactID {
		return apperrors.New(apperrors.ErrInvalidInput, "cannot block yourself")
	}
	return s.contacts.Block(ctx, ownerID, contactID)
}

// UnblockContact lifts a block.
func (s *Service) UnblockContact(ctx context.Context, ownerID, contactID string) error {
	return s.contacts.Unblock(ctx, ownerID, contactID)
}

func (s *Service) SendFriendRequest(ctx context.Context, fromID, toID string) error {
	if fromID == toID {
		return apperrors.New(apperrors.ErrInvalidInput, "cannot send friend request to yourself")
	}
	return s.contacts.SendFriendRequest(ctx, fromID, toID)
}

func (s *Service) ListIncomingFriendRequests(ctx context.Context, accountID string) ([]domain.FriendRequest, error) {
	return s.contacts.ListIncomingFriendRequests(ctx, accountID)
}

func (s *Service) AcceptFriendRequest(ctx context.Context, accountID, fromID string) error {
	if accountID == fromID {
		return apperrors.New(apperrors.ErrInvalidInput, "invalid request")
	}
	return s.contacts.AcceptFriendRequest(ctx, fromID, accountID)
}

func (s *Service) RejectFriendRequest(ctx context.Context, accountID, fromID string) error {
	if accountID == fromID {
		return apperrors.New(apperrors.ErrInvalidInput, "invalid request")
	}
	return s.contacts.RejectFriendRequest(ctx, fromID, accountID)
}

func (s *Service) FriendRelation(ctx context.Context, accountID, peerID string) (string, error) {
	if accountID == peerID {
		return "none", apperrors.New(apperrors.ErrInvalidInput, "invalid peer")
	}
	return s.contacts.FriendRelation(ctx, accountID, peerID)
}

func (s *Service) CanMessage(ctx context.Context, accountID, peerID string) (bool, error) {
	if accountID == peerID {
		return false, apperrors.New(apperrors.ErrInvalidInput, "invalid peer")
	}
	return s.contacts.CanMessage(ctx, accountID, peerID)
}

// FriendSummary is a peer with optional public profile fields for the friends UI.
type FriendSummary struct {
	PeerID      string `json:"peer_id"`
	DisplayName string `json:"display_name,omitempty"`
	Username    string `json:"username,omitempty"`
	AvatarURL   string `json:"avatar_url,omitempty"`
	RelationAt  int64  `json:"relation_at"`
}

// FriendsOverview bundles lists for the desktop friends hub.
type FriendsOverview struct {
	Friends  []FriendSummary `json:"friends"`
	Incoming []FriendSummary `json:"incoming"`
	Outgoing []FriendSummary `json:"outgoing"`
}

// FriendsOverview loads accepted friends and pending requests with profile hydration.
func (s *Service) FriendsOverview(ctx context.Context, accountID string) (FriendsOverview, error) {
	var out FriendsOverview
	inc, err := s.contacts.ListIncomingFriendRequests(ctx, accountID)
	if err != nil {
		return out, err
	}
	outg, err := s.contacts.ListOutgoingFriendRequests(ctx, accountID)
	if err != nil {
		return out, err
	}
	edges, err := s.contacts.ListAcceptedFriendEdges(ctx, accountID)
	if err != nil {
		return out, err
	}

	peerSet := make(map[string]struct{})
	for _, r := range inc {
		peerSet[r.FromID] = struct{}{}
	}
	for _, r := range outg {
		peerSet[r.ToID] = struct{}{}
	}
	for _, e := range edges {
		peerSet[e.PeerID] = struct{}{}
	}
	peers := make([]string, 0, len(peerSet))
	for id := range peerSet {
		peers = append(peers, id)
	}

	profByID := make(map[string]domain.Profile)
	if len(peers) > 0 {
		profs, err := s.profiles.GetBatch(ctx, peers)
		if err != nil {
			return out, err
		}
		for _, p := range profs {
			profByID[p.AccountID] = p
		}
	}

	summarize := func(peerID string, t time.Time) FriendSummary {
		fs := FriendSummary{PeerID: peerID, RelationAt: t.Unix()}
		if p, ok := profByID[peerID]; ok {
			fs.DisplayName = p.DisplayName
			fs.Username = p.Username
			fs.AvatarURL = p.AvatarURL
		}
		return fs
	}

	out.Incoming = make([]FriendSummary, 0, len(inc))
	for _, r := range inc {
		out.Incoming = append(out.Incoming, summarize(r.FromID, r.UpdatedAt))
	}
	out.Outgoing = make([]FriendSummary, 0, len(outg))
	for _, r := range outg {
		out.Outgoing = append(out.Outgoing, summarize(r.ToID, r.UpdatedAt))
	}
	out.Friends = make([]FriendSummary, 0, len(edges))
	for _, e := range edges {
		out.Friends = append(out.Friends, summarize(e.PeerID, e.UpdatedAt))
	}
	return out, nil
}

// UploadPrekeyInput carries the key material an account uploads at registration.
type UploadPrekeyInput struct {
	AccountID      string
	RegistrationID uint32
	IdentityKey    []byte
	SignedPrekeyID uint32
	SignedPrekeyPub []byte
	SignedPrekeySig []byte
	KyberPrekeyID  uint32
	KyberPrekeyPub []byte
	KyberPrekeySig []byte
	OneTimePrekeys []domain.OneTimePrekey
}

// UploadPrekeys saves or replaces an account's key bundle and appends one-time prekeys.
func (s *Service) UploadPrekeys(ctx context.Context, in UploadPrekeyInput) error {
	b := domain.PrekeyBundle{
		AccountID:      in.AccountID,
		RegistrationID: in.RegistrationID,
		IdentityKey:    in.IdentityKey,
		SignedPrekeyID: in.SignedPrekeyID,
		SignedPrekeyPub: in.SignedPrekeyPub,
		SignedPrekeySig: in.SignedPrekeySig,
		KyberPrekeyID:  in.KyberPrekeyID,
		KyberPrekeyPub: in.KyberPrekeyPub,
		KyberPrekeySig: in.KyberPrekeySig,
	}
	if err := s.prekeys.SaveBundle(ctx, b); err != nil {
		return err
	}
	if len(in.OneTimePrekeys) > 0 {
		return s.prekeys.SaveOneTimePrekeys(ctx, in.AccountID, in.OneTimePrekeys)
	}
	return nil
}

// FetchPrekeyBundle returns the key material needed to initiate a session with targetAccountID.
// Includes one optional one-time prekey from the pool if available.
func (s *Service) FetchPrekeyBundle(ctx context.Context, targetAccountID string) (domain.PrekeyBundle, *domain.OneTimePrekey, error) {
	bundle, found, err := s.prekeys.GetBundle(ctx, targetAccountID)
	if err != nil {
		return domain.PrekeyBundle{}, nil, err
	}
	if !found {
		return domain.PrekeyBundle{}, nil, apperrors.New(apperrors.ErrNotFound, "no keys published for this account")
	}
	otpk, err := s.prekeys.PopOneTimePrekey(ctx, targetAccountID)
	if err != nil {
		return domain.PrekeyBundle{}, nil, err
	}
	return bundle, otpk, nil
}
