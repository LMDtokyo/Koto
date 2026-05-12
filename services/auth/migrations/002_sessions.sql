-- Active sessions, one row per device. Created on register/restore, deleted
-- on logout / explicit revoke. Refresh-token hash is stored (not the raw
-- token) so a postgres dump leak does not yield active credentials.

CREATE TABLE IF NOT EXISTS sessions (
    id                 TEXT        PRIMARY KEY,                -- uuid
    account_id         TEXT        NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    refresh_token_hash TEXT        NOT NULL UNIQUE,            -- sha256(opaque refresh token)
    device_name        TEXT        NOT NULL DEFAULT '',        -- "Koto Desktop", "Koto iOS"
    platform           TEXT        NOT NULL DEFAULT '',        -- "Windows", "macOS", "Linux", "iOS", "Android"
    app_version        TEXT        NOT NULL DEFAULT '',
    client_ip          TEXT        NOT NULL DEFAULT '',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sessions_account ON sessions (account_id);
