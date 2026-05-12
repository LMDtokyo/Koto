-- Auth service — PostgreSQL migrations
-- Run with: psql -U $POSTGRES_USER -d koto -f 001_accounts.sql

CREATE TABLE IF NOT EXISTS accounts (
    id                 TEXT        PRIMARY KEY,         -- hex(Ed25519 public key)
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    identity_key       BYTEA       NOT NULL,            -- Ed25519 public key (32 bytes)
    signed_pre_key     BYTEA       NOT NULL,            -- X25519 signed pre-key
    signed_pre_key_sig BYTEA       NOT NULL,            -- Ed25519 signature (64 bytes)
    signed_pre_key_id  INTEGER     NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS one_time_pre_keys (
    id         INTEGER  NOT NULL,
    account_id TEXT     NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    key_data   BYTEA    NOT NULL,
    used       BOOLEAN  NOT NULL DEFAULT FALSE,
    PRIMARY KEY (account_id, id)
);

CREATE INDEX IF NOT EXISTS idx_otpk_account_unused
    ON one_time_pre_keys (account_id) WHERE used = FALSE;
