-- Notification service — PostgreSQL migrations

CREATE TABLE IF NOT EXISTS devices (
    id         TEXT        PRIMARY KEY DEFAULT gen_random_uuid()::text,
    account_id TEXT        NOT NULL,
    token      TEXT        NOT NULL,          -- APNs device token or UnifiedPush endpoint URL
    platform   SMALLINT    NOT NULL,          -- 1=iOS, 2=Android
    bundle_id  TEXT        NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (account_id, token)
);

CREATE INDEX IF NOT EXISTS idx_devices_account ON devices (account_id);
