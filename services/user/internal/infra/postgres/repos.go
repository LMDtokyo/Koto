// Package postgres implements user domain repositories backed by PostgreSQL.
package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/services/user/internal/domain"
)

// ─── ProfileRepo ─────────────────────────────────────────────────────────────

type ProfileRepo struct{ pool *pgxpool.Pool }

func NewProfileRepo(pool *pgxpool.Pool) *ProfileRepo { return &ProfileRepo{pool: pool} }

func (r *ProfileRepo) Upsert(ctx context.Context, p domain.Profile) error {
	// Username is preserved on update — Upsert is for display_name/avatar/banner/bio.
	// Use SetUsername to change the @handle so the unique-violation error
	// path is explicit.
	_, err := r.pool.Exec(ctx,
		`INSERT INTO profiles (account_id, display_name, avatar_url, banner_url, bio, updated_at)
		 VALUES ($1, $2, $3, $4, $5, $6)
		 ON CONFLICT (account_id) DO UPDATE
		    SET display_name = EXCLUDED.display_name,
		        avatar_url   = EXCLUDED.avatar_url,
		        banner_url   = EXCLUDED.banner_url,
		        bio          = EXCLUDED.bio,
		        updated_at   = EXCLUDED.updated_at`,
		p.AccountID, p.DisplayName, p.AvatarURL, p.BannerURL, p.Bio, p.UpdatedAt,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "upsert profile", err)
	}
	return nil
}

func (r *ProfileRepo) Get(ctx context.Context, accountID string) (domain.Profile, error) {
	var p domain.Profile
	err := r.pool.QueryRow(ctx,
		`SELECT account_id, display_name, avatar_url, COALESCE(banner_url, ''), bio, COALESCE(username, ''), updated_at
		 FROM profiles WHERE account_id = $1`, accountID,
	).Scan(&p.AccountID, &p.DisplayName, &p.AvatarURL, &p.BannerURL, &p.Bio, &p.Username, &p.UpdatedAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Profile{AccountID: accountID}, nil // empty profile is ok
		}
		return domain.Profile{}, apperrors.Wrap(apperrors.ErrInternal, "get profile", err)
	}
	return p, nil
}

func (r *ProfileRepo) GetBatch(ctx context.Context, accountIDs []string) ([]domain.Profile, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT account_id, display_name, avatar_url, COALESCE(banner_url, ''), bio, COALESCE(username, ''), updated_at
		 FROM profiles WHERE account_id = ANY($1)`, accountIDs,
	)
	if err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "batch get profiles", err)
	}
	defer rows.Close()

	profiles := make([]domain.Profile, 0, len(accountIDs))
	for rows.Next() {
		var p domain.Profile
		if err := rows.Scan(&p.AccountID, &p.DisplayName, &p.AvatarURL, &p.BannerURL, &p.Bio, &p.Username, &p.UpdatedAt); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan profile", err)
		}
		profiles = append(profiles, p)
	}
	return profiles, rows.Err()
}

func (r *ProfileRepo) IsUsernameTaken(ctx context.Context, username, byAccountID string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(
		    SELECT 1 FROM profiles
		     WHERE LOWER(username) = LOWER($1) AND account_id <> $2
		 )`, username, byAccountID,
	).Scan(&exists)
	if err != nil {
		return false, apperrors.Wrap(apperrors.ErrInternal, "check username", err)
	}
	return exists, nil
}

func (r *ProfileRepo) SetUsername(ctx context.Context, accountID, username string) error {
	tag, err := r.pool.Exec(ctx,
		`INSERT INTO profiles (account_id, username, updated_at)
		 VALUES ($1, $2, NOW())
		 ON CONFLICT (account_id) DO UPDATE
		    SET username   = EXCLUDED.username,
		        updated_at = EXCLUDED.updated_at`,
		accountID, username,
	)
	if err != nil {
		if isUniqueViolation(err) {
			return apperrors.New(apperrors.ErrAlreadyExists, "username taken")
		}
		return apperrors.Wrap(apperrors.ErrInternal, "set username", err)
	}
	if tag.RowsAffected() == 0 {
		return apperrors.New(apperrors.ErrNotFound, "profile row not affected")
	}
	return nil
}

func (r *ProfileRepo) FindByUsername(ctx context.Context, username string) (domain.Profile, error) {
	var p domain.Profile
	err := r.pool.QueryRow(ctx,
		`SELECT account_id, display_name, avatar_url, bio, COALESCE(username, ''), updated_at
		   FROM profiles
		  WHERE LOWER(username) = LOWER($1)`,
		username,
	).Scan(&p.AccountID, &p.DisplayName, &p.AvatarURL, &p.Bio, &p.Username, &p.UpdatedAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Profile{}, apperrors.New(apperrors.ErrNotFound, "username not found")
		}
		return domain.Profile{}, apperrors.Wrap(apperrors.ErrInternal, "find by username", err)
	}
	return p, nil
}

func (r *ProfileRepo) Search(ctx context.Context, query string, limit, offset int) ([]domain.Profile, int, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT account_id, display_name, avatar_url, COALESCE(banner_url, ''), bio, COALESCE(username, ''), updated_at
		   FROM profiles
		  WHERE (
		        username IS NOT NULL
		    AND LOWER(username) LIKE LOWER($1 || '%')
		  ) OR LOWER(display_name) LIKE LOWER('%' || $1 || '%')
		  ORDER BY
		    CASE WHEN username IS NOT NULL AND LOWER(username) LIKE LOWER($1 || '%') THEN 0 ELSE 1 END,
		    updated_at DESC
		  LIMIT $2 OFFSET $3`,
		query, limit+1, offset,
	)
	if err != nil {
		return nil, -1, apperrors.Wrap(apperrors.ErrInternal, "search profiles", err)
	}
	defer rows.Close()

	out := make([]domain.Profile, 0, limit+1)
	for rows.Next() {
		var p domain.Profile
		if err := rows.Scan(&p.AccountID, &p.DisplayName, &p.AvatarURL, &p.BannerURL, &p.Bio, &p.Username, &p.UpdatedAt); err != nil {
			return nil, -1, apperrors.Wrap(apperrors.ErrInternal, "scan searched profile", err)
		}
		out = append(out, p)
	}
	if err := rows.Err(); err != nil {
		return nil, -1, apperrors.Wrap(apperrors.ErrInternal, "iterate searched profiles", err)
	}

	next := -1
	if len(out) > limit {
		out = out[:limit]
		next = offset + limit
	}
	return out, next, nil
}

func isUniqueViolation(err error) bool {
	var pgErr interface{ SQLState() string }
	if errors.As(err, &pgErr) {
		return pgErr.SQLState() == "23505"
	}
	return false
}

// ─── ContactRepo ─────────────────────────────────────────────────────────────

type ContactRepo struct{ pool *pgxpool.Pool }

func NewContactRepo(pool *pgxpool.Pool) *ContactRepo { return &ContactRepo{pool: pool} }

func (r *ContactRepo) Add(ctx context.Context, c domain.Contact) error {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO contacts (owner_id, contact_id, nickname, added_at, blocked)
		 VALUES ($1, $2, $3, $4, false)
		 ON CONFLICT (owner_id, contact_id) DO NOTHING`,
		c.OwnerID, c.ContactID, c.Nickname, c.AddedAt,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "add contact", err)
	}
	return nil
}

func (r *ContactRepo) Remove(ctx context.Context, ownerID, contactID string) error {
	_, err := r.pool.Exec(ctx,
		`DELETE FROM contacts WHERE owner_id = $1 AND contact_id = $2`,
		ownerID, contactID,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "remove contact", err)
	}
	return nil
}

func (r *ContactRepo) List(ctx context.Context, ownerID string) ([]domain.Contact, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT owner_id, contact_id, nickname, added_at, blocked
		 FROM contacts WHERE owner_id = $1 AND blocked = false ORDER BY added_at DESC`,
		ownerID,
	)
	if err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "list contacts", err)
	}
	defer rows.Close()

	var contacts []domain.Contact
	for rows.Next() {
		var c domain.Contact
		if err := rows.Scan(&c.OwnerID, &c.ContactID, &c.Nickname, &c.AddedAt, &c.Blocked); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan contact", err)
		}
		contacts = append(contacts, c)
	}
	return contacts, rows.Err()
}

func (r *ContactRepo) Block(ctx context.Context, ownerID, contactID string) error {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO contacts (owner_id, contact_id, nickname, added_at, blocked)
		 VALUES ($1, $2, '', $3, true)
		 ON CONFLICT (owner_id, contact_id) DO UPDATE SET blocked = true`,
		ownerID, contactID, time.Now().UTC(),
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "block contact", err)
	}
	return nil
}

func (r *ContactRepo) Unblock(ctx context.Context, ownerID, contactID string) error {
	_, err := r.pool.Exec(ctx,
		`UPDATE contacts SET blocked = false WHERE owner_id = $1 AND contact_id = $2`,
		ownerID, contactID,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "unblock contact", err)
	}
	return nil
}

func (r *ContactRepo) SendFriendRequest(ctx context.Context, fromID, toID string) error {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO friend_requests (from_id, to_id, status, created_at, updated_at)
		 VALUES ($1, $2, 'pending', NOW(), NOW())
		 ON CONFLICT (from_id, to_id) DO UPDATE SET
		    status = CASE
		      WHEN friend_requests.status = 'accepted' THEN 'accepted'
		      ELSE 'pending'
		    END,
		    updated_at = NOW()`,
		fromID, toID,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "send friend request", err)
	}
	return nil
}

func (r *ContactRepo) ListIncomingFriendRequests(ctx context.Context, accountID string) ([]domain.FriendRequest, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT from_id, to_id, status, created_at, updated_at
		   FROM friend_requests
		  WHERE to_id = $1 AND status = 'pending'
		  ORDER BY created_at DESC`,
		accountID,
	)
	if err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "list incoming requests", err)
	}
	defer rows.Close()

	out := make([]domain.FriendRequest, 0)
	for rows.Next() {
		var x domain.FriendRequest
		if err := rows.Scan(&x.FromID, &x.ToID, &x.Status, &x.CreatedAt, &x.UpdatedAt); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan incoming request", err)
		}
		out = append(out, x)
	}
	return out, rows.Err()
}

func (r *ContactRepo) ListOutgoingFriendRequests(ctx context.Context, accountID string) ([]domain.FriendRequest, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT from_id, to_id, status, created_at, updated_at
		   FROM friend_requests
		  WHERE from_id = $1 AND status = 'pending'
		  ORDER BY created_at DESC`,
		accountID,
	)
	if err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "list outgoing requests", err)
	}
	defer rows.Close()

	out := make([]domain.FriendRequest, 0)
	for rows.Next() {
		var x domain.FriendRequest
		if err := rows.Scan(&x.FromID, &x.ToID, &x.Status, &x.CreatedAt, &x.UpdatedAt); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan outgoing request", err)
		}
		out = append(out, x)
	}
	return out, rows.Err()
}

func (r *ContactRepo) ListAcceptedFriendEdges(ctx context.Context, accountID string) ([]domain.AcceptedFriendEdge, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT CASE WHEN from_id = $1 THEN to_id ELSE from_id END AS peer_id, updated_at
		   FROM friend_requests
		  WHERE status = 'accepted' AND (from_id = $1 OR to_id = $1)
		  ORDER BY updated_at DESC`,
		accountID,
	)
	if err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "list accepted friends", err)
	}
	defer rows.Close()

	out := make([]domain.AcceptedFriendEdge, 0)
	for rows.Next() {
		var e domain.AcceptedFriendEdge
		if err := rows.Scan(&e.PeerID, &e.UpdatedAt); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan accepted friend", err)
		}
		out = append(out, e)
	}
	return out, rows.Err()
}

func (r *ContactRepo) AcceptFriendRequest(ctx context.Context, fromID, toID string) error {
	tag, err := r.pool.Exec(ctx,
		`UPDATE friend_requests
		    SET status = 'accepted', updated_at = NOW()
		  WHERE from_id = $1 AND to_id = $2 AND status = 'pending'`,
		fromID, toID,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "accept friend request", err)
	}
	if tag.RowsAffected() == 0 {
		return apperrors.New(apperrors.ErrNotFound, "friend request not found")
	}
	return nil
}

func (r *ContactRepo) RejectFriendRequest(ctx context.Context, fromID, toID string) error {
	tag, err := r.pool.Exec(ctx,
		`UPDATE friend_requests
		    SET status = 'rejected', updated_at = NOW()
		  WHERE from_id = $1 AND to_id = $2 AND status = 'pending'`,
		fromID, toID,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "reject friend request", err)
	}
	if tag.RowsAffected() == 0 {
		return apperrors.New(apperrors.ErrNotFound, "friend request not found")
	}
	return nil
}

func (r *ContactRepo) FriendRelation(ctx context.Context, accountID, peerID string) (string, error) {
	var status string
	err := r.pool.QueryRow(ctx,
		`SELECT status
		   FROM friend_requests
		  WHERE (from_id = $1 AND to_id = $2)
		     OR (from_id = $2 AND to_id = $1)
		  ORDER BY updated_at DESC
		  LIMIT 1`,
		accountID, peerID,
	).Scan(&status)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return "none", nil
		}
		return "none", apperrors.Wrap(apperrors.ErrInternal, "friend relation", err)
	}
	if status == "accepted" {
		return "accepted", nil
	}
	var fromID, toID string
	err = r.pool.QueryRow(ctx,
		`SELECT from_id, to_id
		   FROM friend_requests
		  WHERE (from_id = $1 AND to_id = $2)
		     OR (from_id = $2 AND to_id = $1)
		  ORDER BY updated_at DESC
		  LIMIT 1`,
		accountID, peerID,
	).Scan(&fromID, &toID)
	if err != nil {
		return "none", apperrors.Wrap(apperrors.ErrInternal, "friend relation direction", err)
	}
	if status == "pending" {
		if fromID == accountID && toID == peerID {
			return "outgoing_pending", nil
		}
		if fromID == peerID && toID == accountID {
			return "incoming_pending", nil
		}
	}
	return "none", nil
}

func (r *ContactRepo) CanMessage(ctx context.Context, accountID, peerID string) (bool, error) {
	var ok bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(
		    SELECT 1 FROM friend_requests
		     WHERE status = 'accepted'
		       AND (
		         (from_id = $1 AND to_id = $2)
		         OR
		         (from_id = $2 AND to_id = $1)
		       )
		 )`,
		accountID, peerID,
	).Scan(&ok)
	if err != nil {
		return false, apperrors.Wrap(apperrors.ErrInternal, "can message check", err)
	}
	return ok, nil
}

// ─── PrekeyRepo ───────────────────────────────────────────────────────────────

type PrekeyRepo struct{ pool *pgxpool.Pool }

func NewPrekeyRepo(pool *pgxpool.Pool) *PrekeyRepo { return &PrekeyRepo{pool: pool} }

func (r *PrekeyRepo) SaveBundle(ctx context.Context, b domain.PrekeyBundle) error {
    _, err := r.pool.Exec(ctx,
        `INSERT INTO prekey_bundles
            (account_id, registration_id, identity_key,
             signed_prekey_id, signed_prekey_pub, signed_prekey_sig,
             kyber_prekey_id, kyber_prekey_pub, kyber_prekey_sig,
             updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,NOW())
         ON CONFLICT (account_id) DO UPDATE SET
             registration_id   = EXCLUDED.registration_id,
             identity_key      = EXCLUDED.identity_key,
             signed_prekey_id  = EXCLUDED.signed_prekey_id,
             signed_prekey_pub = EXCLUDED.signed_prekey_pub,
             signed_prekey_sig = EXCLUDED.signed_prekey_sig,
             kyber_prekey_id   = EXCLUDED.kyber_prekey_id,
             kyber_prekey_pub  = EXCLUDED.kyber_prekey_pub,
             kyber_prekey_sig  = EXCLUDED.kyber_prekey_sig,
             updated_at        = NOW()`,
        b.AccountID, b.RegistrationID, b.IdentityKey,
        b.SignedPrekeyID, b.SignedPrekeyPub, b.SignedPrekeySig,
        b.KyberPrekeyID, b.KyberPrekeyPub, b.KyberPrekeySig,
    )
    if err != nil {
        return apperrors.Wrap(apperrors.ErrInternal, "save prekey bundle", err)
    }
    return nil
}

// SaveOneTimePrekeys полностью ЗАМЕНЯЕТ bundle OTPK для аккаунта.
// Раньше использовался ON CONFLICT DO NOTHING — при повторной публикации
// (каждый запуск клиента генерит новый bundle) сервер хранил СТАРЫЕ pubkeys,
// а локальный store имел НОВЫЕ privates. ID совпадали, но pub/priv из разных
// bundle'ов → libsignal "Bad MAC" при decrypt у получателя. UPSERT с
// public_key = EXCLUDED.public_key выравнивает: новый pubkey для каждого id,
// + допускает повторную публикацию после того как часть OTPK была consumed.
func (r *PrekeyRepo) SaveOneTimePrekeys(ctx context.Context, accountID string, keys []domain.OneTimePrekey) error {
    for _, k := range keys {
        _, err := r.pool.Exec(ctx,
            `INSERT INTO one_time_prekeys (account_id, prekey_id, public_key)
             VALUES ($1, $2, $3)
             ON CONFLICT (account_id, prekey_id) DO UPDATE SET
                 public_key = EXCLUDED.public_key`,
            accountID, k.PrekeyID, k.PublicKey,
        )
        if err != nil {
            return apperrors.Wrap(apperrors.ErrInternal, "save one-time prekey", err)
        }
    }
    return nil
}

func (r *PrekeyRepo) GetBundle(ctx context.Context, accountID string) (domain.PrekeyBundle, bool, error) {
    var b domain.PrekeyBundle
    err := r.pool.QueryRow(ctx,
        `SELECT account_id, registration_id, identity_key,
                signed_prekey_id, signed_prekey_pub, signed_prekey_sig,
                kyber_prekey_id, kyber_prekey_pub, kyber_prekey_sig
         FROM prekey_bundles WHERE account_id = $1`, accountID,
    ).Scan(
        &b.AccountID, &b.RegistrationID, &b.IdentityKey,
        &b.SignedPrekeyID, &b.SignedPrekeyPub, &b.SignedPrekeySig,
        &b.KyberPrekeyID, &b.KyberPrekeyPub, &b.KyberPrekeySig,
    )
    if err != nil {
        if errors.Is(err, pgx.ErrNoRows) {
            return domain.PrekeyBundle{}, false, nil
        }
        return domain.PrekeyBundle{}, false, apperrors.Wrap(apperrors.ErrInternal, "get prekey bundle", err)
    }
    return b, true, nil
}

func (r *PrekeyRepo) PopOneTimePrekey(ctx context.Context, accountID string) (*domain.OneTimePrekey, error) {
    var k domain.OneTimePrekey
    err := r.pool.QueryRow(ctx,
        `DELETE FROM one_time_prekeys
         WHERE id = (
             SELECT id FROM one_time_prekeys
             WHERE account_id = $1
             ORDER BY id
             LIMIT 1
             FOR UPDATE SKIP LOCKED
         )
         RETURNING account_id, prekey_id, public_key`,
        accountID,
    ).Scan(&k.AccountID, &k.PrekeyID, &k.PublicKey)
    if err != nil {
        if errors.Is(err, pgx.ErrNoRows) {
            return nil, nil
        }
        return nil, apperrors.Wrap(apperrors.ErrInternal, "pop one-time prekey", err)
    }
    return &k, nil
}

// NewPool creates a PostgreSQL connection pool.
func NewPool(ctx context.Context, dsn string) (*pgxpool.Pool, error) {
	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, err
	}
	cfg.MaxConnLifetime = time.Hour
	cfg.MaxConnIdleTime = 30 * time.Minute

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, err
	}
	return pool, pool.Ping(ctx)
}
