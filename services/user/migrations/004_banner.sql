-- Banner — Discord-style header image on the profile card. Stored as a media
-- file id; the client resolves to a URL via the media service. Optional;
-- empty string treated as "no banner" so the schema stays NOT NULL.

ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS banner_url TEXT NOT NULL DEFAULT '';
