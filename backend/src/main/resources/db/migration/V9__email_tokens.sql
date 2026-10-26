-- Email verification and password reset.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
-- Accounts that exist before this migration were created without verification; they are treated as verified.
UPDATE users SET email_verified_at = created_at;

-- One-time links. Only the SHA-256 hash is stored, so a database leak does not leak usable links.
CREATE TABLE email_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       VARCHAR(10) NOT NULL CHECK (kind IN ('VERIFY', 'RESET')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);
CREATE INDEX idx_email_tokens_user_kind ON email_tokens (user_id, kind, created_at DESC);
