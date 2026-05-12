-- Reactions on messages. One row per (conv, msg, actor, emoji) — toggling is
-- INSERT or DELETE on this composite key. Partitioned by conversation so a
-- chat-screen "load reactions for these messages" query stays inside one
-- partition; clustered by msg_id then actor_id then emoji for cheap lookup.

USE koto;

CREATE TABLE IF NOT EXISTS message_reactions (
    conversation_id TEXT,
    msg_id          TIMEUUID,
    actor_id        TEXT,
    emoji           TEXT,
    reacted_at      TIMESTAMP,
    PRIMARY KEY ((conversation_id), msg_id, actor_id, emoji)
) WITH CLUSTERING ORDER BY (msg_id DESC, actor_id ASC, emoji ASC);
