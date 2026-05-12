-- Media service — PostgreSQL migrations

CREATE TABLE IF NOT EXISTS files (
    id           TEXT        PRIMARY KEY,
    account_id   TEXT        NOT NULL,
    object_key   TEXT        NOT NULL UNIQUE,
    content_type TEXT        NOT NULL DEFAULT 'application/octet-stream',
    size_bytes   BIGINT      NOT NULL DEFAULT 0,
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_files_account ON files (account_id);
