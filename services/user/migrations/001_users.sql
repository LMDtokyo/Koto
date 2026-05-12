-- User service — PostgreSQL migrations

CREATE TABLE IF NOT EXISTS profiles (
    account_id   TEXT        PRIMARY KEY,
    display_name TEXT        NOT NULL DEFAULT '',
    avatar_url   TEXT        NOT NULL DEFAULT '',
    bio          TEXT        NOT NULL DEFAULT '',
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS contacts (
    owner_id   TEXT        NOT NULL,
    contact_id TEXT        NOT NULL,
    nickname   TEXT        NOT NULL DEFAULT '',
    added_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    blocked    BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (owner_id, contact_id)
);

CREATE INDEX IF NOT EXISTS idx_contacts_owner ON contacts (owner_id) WHERE blocked = FALSE;
