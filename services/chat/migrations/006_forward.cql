-- Forwarded-from marker on a message. When non-empty, the receiver renders
-- a "Переслано от <name>" header above the bubble. The string holds the
-- account id of the *original* author (not the user doing the forward) so
-- the receiver can fetch their profile to build the label.

USE koto;

ALTER TABLE messages ADD forwarded_from TEXT;
