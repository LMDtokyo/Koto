-- Group conversations get a user-supplied display name so the receiver
-- side doesn't have to guess from member IDs. Empty for direct chats —
-- those still derive their label from the peer's profile.

USE koto;

ALTER TABLE conversations ADD name TEXT;
