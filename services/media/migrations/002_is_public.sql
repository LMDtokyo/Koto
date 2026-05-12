-- Allow files to be marked as publicly readable (e.g. avatars).
-- Without this, every download requires the requester to be the uploader.

ALTER TABLE files ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial index — only non-trivial for reads when is_public = true
CREATE INDEX IF NOT EXISTS idx_files_public ON files (id) WHERE is_public = TRUE;
