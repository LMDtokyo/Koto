-- Bot service — PostgreSQL migrations

CREATE TABLE IF NOT EXISTS bots (
    id          TEXT        PRIMARY KEY,
    account_id  TEXT        NOT NULL UNIQUE,  -- bot's messenger account ID
    owner_id    TEXT        NOT NULL,          -- developer account
    name        TEXT        NOT NULL,
    username    TEXT        NOT NULL UNIQUE,
    token       TEXT        NOT NULL UNIQUE,   -- webhook auth token
    webhook_url TEXT        NOT NULL DEFAULT '',
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bots_owner    ON bots (owner_id);
CREATE INDEX IF NOT EXISTS idx_bots_username ON bots (username);
CREATE INDEX IF NOT EXISTS idx_bots_account  ON bots (account_id);
