-- Session/refresh-token registry, phuc vu UC-01 logout va BR-AUTH-04
-- (revoke-all-sessions khi tai khoan bi khoa)

CREATE TABLE IF NOT EXISTS user_sessions (
    session_id             UUID PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    refresh_token_hash      VARCHAR(255) NOT NULL,
    user_agent               VARCHAR(255),
    ip_address                VARCHAR(64),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at                TIMESTAMPTZ NOT NULL,
    revoked_at                TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_active ON user_sessions (user_id) WHERE revoked_at IS NULL;
