-- User service — PreKey tables for Signal Protocol (X3DH + PQXDH key exchange)

-- Stores the long-lived key material per account (identity key, signed prekey, Kyber prekey).
CREATE TABLE IF NOT EXISTS prekey_bundles (
    account_id          TEXT        PRIMARY KEY,
    registration_id     INTEGER     NOT NULL,
    identity_key        BYTEA       NOT NULL,
    signed_prekey_id    INTEGER     NOT NULL,
    signed_prekey_pub   BYTEA       NOT NULL,
    signed_prekey_sig   BYTEA       NOT NULL,
    kyber_prekey_id     INTEGER     NOT NULL,
    kyber_prekey_pub    BYTEA       NOT NULL,
    kyber_prekey_sig    BYTEA       NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One-time prekeys uploaded in batches; one row is consumed (deleted) per new session.
CREATE TABLE IF NOT EXISTS one_time_prekeys (
    id          BIGSERIAL   PRIMARY KEY,
    account_id  TEXT        NOT NULL,
    prekey_id   INTEGER     NOT NULL,
    public_key  BYTEA       NOT NULL,
    UNIQUE (account_id, prekey_id)
);

CREATE INDEX IF NOT EXISTS idx_one_time_prekeys_account ON one_time_prekeys (account_id);
