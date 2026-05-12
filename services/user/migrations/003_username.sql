-- Username — public @handle for an account, like Telegram / Discord.
--
-- Stored lower-cased so case-insensitive uniqueness is enforced by a single
-- UNIQUE constraint. Nullable because users register without one and pick
-- it later (or never).

ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS username TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_username_lower
    ON profiles (LOWER(username))
    WHERE username IS NOT NULL;
