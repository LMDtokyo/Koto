// Package postgres implements domain repositories backed by PostgreSQL via pgx v5.
package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/services/auth/internal/domain"
)

// AccountRepo implements domain.AccountRepository.
type AccountRepo struct{ pool *pgxpool.Pool }

// NewAccountRepo creates a new AccountRepo backed by the given connection pool.
func NewAccountRepo(pool *pgxpool.Pool) *AccountRepo {
	return &AccountRepo{pool: pool}
}

func (r *AccountRepo) Create(ctx context.Context, a domain.Account) error {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO accounts
		    (id, created_at, identity_key, signed_pre_key, signed_pre_key_sig, signed_pre_key_id)
		 VALUES ($1, $2, $3, $4, $5, $6)`,
		a.ID, a.CreatedAt, a.IdentityKey, a.SignedPreKey, a.SignedPreKeySig, a.SignedPreKeyID,
	)
	if err != nil {
		if isUniqueViolation(err) {
			return apperrors.New(apperrors.ErrAlreadyExists, "account already exists")
		}
		return apperrors.Wrap(apperrors.ErrInternal, "create account", err)
	}
	return nil
}

func (r *AccountRepo) Get(ctx context.Context, id string) (domain.Account, error) {
	var a domain.Account
	err := r.pool.QueryRow(ctx,
		`SELECT id, created_at, identity_key, signed_pre_key, signed_pre_key_sig, signed_pre_key_id
		 FROM accounts WHERE id = $1`, id,
	).Scan(&a.ID, &a.CreatedAt, &a.IdentityKey, &a.SignedPreKey, &a.SignedPreKeySig, &a.SignedPreKeyID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Account{}, apperrors.New(apperrors.ErrNotFound, "account not found")
		}
		return domain.Account{}, apperrors.Wrap(apperrors.ErrInternal, "get account", err)
	}
	return a, nil
}

func (r *AccountRepo) Exists(ctx context.Context, id string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM accounts WHERE id = $1)`, id,
	).Scan(&exists)
	if err != nil {
		return false, apperrors.Wrap(apperrors.ErrInternal, "check account exists", err)
	}
	return exists, nil
}

func (r *AccountRepo) UpdateKeys(ctx context.Context, id string, signedPreKey, signedPreKeySig []byte, signedPreKeyID uint32) error {
	tag, err := r.pool.Exec(ctx,
		`UPDATE accounts
		    SET signed_pre_key     = $2,
		        signed_pre_key_sig = $3,
		        signed_pre_key_id  = $4
		  WHERE id = $1`,
		id, signedPreKey, signedPreKeySig, signedPreKeyID,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "update keys", err)
	}
	if tag.RowsAffected() == 0 {
		return apperrors.New(apperrors.ErrNotFound, "account not found")
	}
	return nil
}

// ─── PreKeyRepo ───────────────────────────────────────────────────────────────

// PreKeyRepo implements domain.PreKeyRepository.
type PreKeyRepo struct{ pool *pgxpool.Pool }

func NewPreKeyRepo(pool *pgxpool.Pool) *PreKeyRepo {
	return &PreKeyRepo{pool: pool}
}

func (r *PreKeyRepo) Save(ctx context.Context, keys []domain.OneTimePreKey) error {
	batch := &pgx.Batch{}
	for _, k := range keys {
		batch.Queue(
			`INSERT INTO one_time_pre_keys (id, account_id, key_data, used)
			 VALUES ($1, $2, $3, false)
			 ON CONFLICT (account_id, id) DO NOTHING`,
			k.ID, k.AccountID, k.KeyData,
		)
	}
	br := r.pool.SendBatch(ctx, batch)
	defer br.Close()

	for range keys {
		if _, err := br.Exec(); err != nil {
			return apperrors.Wrap(apperrors.ErrInternal, "save pre-keys", err)
		}
	}
	return nil
}

func (r *PreKeyRepo) Pop(ctx context.Context, accountID string) (domain.OneTimePreKey, error) {
	var k domain.OneTimePreKey
	// CTE-based atomic pop: mark as used and return
	err := r.pool.QueryRow(ctx,
		`WITH selected AS (
		    SELECT id, account_id, key_data
		    FROM one_time_pre_keys
		    WHERE account_id = $1 AND used = false
		    LIMIT 1
		    FOR UPDATE SKIP LOCKED
		 )
		 UPDATE one_time_pre_keys
		    SET used = true
		   FROM selected
		  WHERE one_time_pre_keys.account_id = selected.account_id
		    AND one_time_pre_keys.id = selected.id
		 RETURNING one_time_pre_keys.id, one_time_pre_keys.account_id, one_time_pre_keys.key_data`,
		accountID,
	).Scan(&k.ID, &k.AccountID, &k.KeyData)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.OneTimePreKey{}, apperrors.New(apperrors.ErrNotFound, "no one-time pre-keys available")
		}
		return domain.OneTimePreKey{}, apperrors.Wrap(apperrors.ErrInternal, "pop pre-key", err)
	}
	return k, nil
}

func (r *PreKeyRepo) Count(ctx context.Context, accountID string) (int, error) {
	var count int
	err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM one_time_pre_keys WHERE account_id = $1 AND used = false`,
		accountID,
	).Scan(&count)
	if err != nil {
		return 0, apperrors.Wrap(apperrors.ErrInternal, "count pre-keys", err)
	}
	return count, nil
}

func (r *PreKeyRepo) Replace(ctx context.Context, accountID string, keys []domain.OneTimePreKey) error {
	tx, err := r.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "begin tx", err)
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx, `DELETE FROM one_time_pre_keys WHERE account_id = $1`, accountID); err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "delete old pre-keys", err)
	}
	for _, k := range keys {
		if _, err := tx.Exec(ctx,
			`INSERT INTO one_time_pre_keys (id, account_id, key_data, used)
			 VALUES ($1, $2, $3, false)`,
			k.ID, k.AccountID, k.KeyData,
		); err != nil {
			return apperrors.Wrap(apperrors.ErrInternal, "insert pre-key", err)
		}
	}
	if err := tx.Commit(ctx); err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "commit replace", err)
	}
	return nil
}

// ─── SessionRepo ─────────────────────────────────────────────────────────────

type SessionRepo struct{ pool *pgxpool.Pool }

func NewSessionRepo(pool *pgxpool.Pool) *SessionRepo { return &SessionRepo{pool: pool} }

func (r *SessionRepo) Create(ctx context.Context, s domain.Session) error {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO sessions
		    (id, account_id, refresh_token_hash, device_name, platform, app_version, client_ip)
		 VALUES ($1, $2, $3, $4, $5, $6, $7)`,
		s.ID, s.AccountID, s.RefreshTokenHash, s.DeviceName, s.Platform, s.AppVersion, s.ClientIP,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "create session", err)
	}
	return nil
}

func (r *SessionRepo) ListForAccount(ctx context.Context, accountID string) ([]domain.Session, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, account_id, refresh_token_hash, device_name, platform, app_version, client_ip,
		        created_at, last_seen_at
		   FROM sessions
		  WHERE account_id = $1
		  ORDER BY last_seen_at DESC`,
		accountID,
	)
	if err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "list sessions", err)
	}
	defer rows.Close()

	out := make([]domain.Session, 0)
	for rows.Next() {
		var s domain.Session
		if err := rows.Scan(&s.ID, &s.AccountID, &s.RefreshTokenHash, &s.DeviceName,
			&s.Platform, &s.AppVersion, &s.ClientIP, &s.CreatedAt, &s.LastSeenAt); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan session", err)
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

func (r *SessionRepo) GetByRefreshTokenHash(ctx context.Context, hash string) (domain.Session, error) {
	var s domain.Session
	err := r.pool.QueryRow(ctx,
		`SELECT id, account_id, refresh_token_hash, device_name, platform, app_version, client_ip,
		        created_at, last_seen_at
		   FROM sessions
		  WHERE refresh_token_hash = $1`,
		hash,
	).Scan(&s.ID, &s.AccountID, &s.RefreshTokenHash, &s.DeviceName,
		&s.Platform, &s.AppVersion, &s.ClientIP, &s.CreatedAt, &s.LastSeenAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Session{}, apperrors.New(apperrors.ErrNotFound, "session not found")
		}
		return domain.Session{}, apperrors.Wrap(apperrors.ErrInternal, "get session", err)
	}
	return s, nil
}

func (r *SessionRepo) Touch(ctx context.Context, sessionID, newRefreshTokenHash, deviceName, platform, appVersion, clientIP string) error {
	tag, err := r.pool.Exec(ctx,
		`UPDATE sessions
		    SET refresh_token_hash = $2,
		        last_seen_at       = NOW(),
		        device_name        = CASE WHEN device_name = '' THEN $3 ELSE device_name END,
		        platform           = CASE WHEN platform    = '' THEN $4 ELSE platform    END,
		        app_version        = CASE WHEN app_version = '' THEN $5 ELSE app_version END,
		        client_ip          = CASE WHEN client_ip   = '' THEN $6 ELSE client_ip   END
		  WHERE id = $1`,
		sessionID, newRefreshTokenHash, deviceName, platform, appVersion, clientIP,
	)
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "touch session", err)
	}
	if tag.RowsAffected() == 0 {
		return apperrors.New(apperrors.ErrNotFound, "session vanished during refresh")
	}
	return nil
}

func (r *SessionRepo) RevokeByID(ctx context.Context, accountID, sessionID string) (domain.Session, error) {
	var s domain.Session
	err := r.pool.QueryRow(ctx,
		`DELETE FROM sessions
		  WHERE id = $1 AND account_id = $2
		 RETURNING id, account_id, refresh_token_hash, device_name, platform, app_version, client_ip,
		           created_at, last_seen_at`,
		sessionID, accountID,
	).Scan(&s.ID, &s.AccountID, &s.RefreshTokenHash, &s.DeviceName,
		&s.Platform, &s.AppVersion, &s.ClientIP, &s.CreatedAt, &s.LastSeenAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Session{}, apperrors.New(apperrors.ErrNotFound, "session not found")
		}
		return domain.Session{}, apperrors.Wrap(apperrors.ErrInternal, "revoke session", err)
	}
	return s, nil
}

func (r *SessionRepo) RevokeOthers(ctx context.Context, accountID, keepSessionID string) ([]domain.Session, error) {
	rows, err := r.pool.Query(ctx,
		`DELETE FROM sessions
		  WHERE account_id = $1 AND id <> $2
		 RETURNING id, account_id, refresh_token_hash, device_name, platform, app_version, client_ip,
		           created_at, last_seen_at`,
		accountID, keepSessionID,
	)
	if err != nil {
		return nil, apperrors.Wrap(apperrors.ErrInternal, "revoke other sessions", err)
	}
	defer rows.Close()

	out := make([]domain.Session, 0)
	for rows.Next() {
		var s domain.Session
		if err := rows.Scan(&s.ID, &s.AccountID, &s.RefreshTokenHash, &s.DeviceName,
			&s.Platform, &s.AppVersion, &s.ClientIP, &s.CreatedAt, &s.LastSeenAt); err != nil {
			return nil, apperrors.Wrap(apperrors.ErrInternal, "scan revoked session", err)
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

// ─── helpers ─────────────────────────────────────────────────────────────────

func isUniqueViolation(err error) bool {
	// pgx wraps Postgres error codes in *pgconn.PgError
	var pgErr interface{ SQLState() string }
	if errors.As(err, &pgErr) {
		return pgErr.SQLState() == "23505"
	}
	return false
}

// NewPool creates a pgxpool from a DSN string.
func NewPool(ctx context.Context, dsn string) (*pgxpool.Pool, error) {
	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, err
	}
	// Ensure closed connections don't block graceful shutdown
	cfg.MaxConnLifetime = time.Hour
	cfg.MaxConnIdleTime = 30 * time.Minute

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, err
	}
	if err := pool.Ping(ctx); err != nil {
		return nil, err
	}
	return pool, nil
}
