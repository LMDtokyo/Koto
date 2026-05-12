-- Жалобы пользователей. Plaintext хранится только когда юзер сам нажал
-- «Пожаловаться» (он добровольно отправил содержимое нам).
CREATE TABLE IF NOT EXISTS moderation_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id     TEXT NOT NULL,
    reported_id     TEXT NOT NULL,
    conversation_id TEXT,
    message_id      TEXT,
    reason          TEXT NOT NULL DEFAULT 'other',
    -- Plaintext, который передал репортер (decrypted на его клиенте перед отправкой).
    plaintext       TEXT,
    -- 5 предыдущих сообщений в чате (контекст), также plaintext.
    context         JSONB,
    -- Auto-classification: ok / spam / harassment / csam / terrorism / illegal / other.
    classification  TEXT,
    classification_score REAL,
    -- Решение модератора.
    status          TEXT NOT NULL DEFAULT 'pending', -- pending | reviewed | dismissed | actioned
    moderator_id    TEXT,
    moderator_note  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_moderation_reports_reported ON moderation_reports(reported_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_reports_status ON moderation_reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_reports_classification ON moderation_reports(classification, created_at DESC);

-- Аккаунты, заблокированные модераторами по результатам разбирательства.
CREATE TABLE IF NOT EXISTS moderation_actions (
    account_id    TEXT PRIMARY KEY,
    action        TEXT NOT NULL,        -- warn | mute | ban | shadowban
    reason        TEXT,
    moderator_id  TEXT,
    expires_at    TIMESTAMPTZ,          -- NULL = perpetual
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
