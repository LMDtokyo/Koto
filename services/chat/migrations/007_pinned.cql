-- Pin marker on a message — boolean per row. Telegram-style: any chat
-- member can pin/unpin in 1:1; for groups we keep the same rule (admin
-- gating can come later when group roles arrive). The pinned-list query
-- still scans the partition; if pinned messages get hot we'll index them
-- in a side table.

USE koto;

ALTER TABLE messages ADD pinned BOOLEAN;
