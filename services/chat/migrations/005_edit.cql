-- Edit timestamp on a message. NULL = never edited. The server replaces the
-- stored ciphertext on edit (the new ciphertext decrypts to the new
-- plaintext under the same Signal session) and stamps this column so
-- recipients can render an "(изменено)" badge.

USE koto;

ALTER TABLE messages ADD edited_at TIMESTAMP;
