CREATE TABLE IF NOT EXISTS friend_requests (
    from_id     TEXT        NOT NULL,
    to_id       TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'pending',
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (from_id, to_id),
    CONSTRAINT friend_requests_status_chk CHECK (status IN ('pending', 'accepted', 'rejected'))
);

CREATE INDEX IF NOT EXISTS idx_friend_requests_to_pending
    ON friend_requests (to_id, status, created_at DESC);
