-- Chat service — ScyllaDB (CQL) migrations
-- Run with: cqlsh -f 001_messages.cql

CREATE KEYSPACE IF NOT EXISTS koto
    WITH replication = {
        'class': 'NetworkTopologyStrategy',
        'datacenter1': 1   -- single-node dev (docker); raise RF for multi-rack production clusters
    }
    AND durable_writes = true;

USE koto;

-- Messages partitioned by conversation_id, ordered by time (newest first).
-- TWCS ensures compaction within daily time windows — critical for time-series performance.
CREATE TABLE IF NOT EXISTS messages (
    conversation_id TEXT,
    msg_id          TIMEUUID,
    sender_id       TEXT,
    type            TINYINT,
    ciphertext      BLOB,
    sender_key_data BLOB,
    client_seq      BIGINT,
    sent_at         TIMESTAMP,
    PRIMARY KEY (conversation_id, msg_id)
) WITH CLUSTERING ORDER BY (msg_id DESC)
  AND compaction = {
    'class': 'TimeWindowCompactionStrategy',
    'compaction_window_size': '1',
    'compaction_window_unit': 'DAYS'
  }
  AND gc_grace_seconds = 86400
  AND default_time_to_live = 0;  -- per-message TTL set on insert for disappearing messages

-- Conversation metadata
CREATE TABLE IF NOT EXISTS conversations (
    id          TIMEUUID,
    type        TINYINT,
    member_ids  LIST<TEXT>,
    created_at  TIMESTAMP,
    last_msg_id TEXT,
    last_msg_at TIMESTAMP,
    PRIMARY KEY (id)
);

-- Index: member → conversations (for loading inbox)
CREATE TABLE IF NOT EXISTS member_conversations (
    member_id  TEXT,
    conv_id    TIMEUUID,
    created_at TIMESTAMP,
    PRIMARY KEY (member_id, conv_id)
) WITH CLUSTERING ORDER BY (conv_id DESC);
