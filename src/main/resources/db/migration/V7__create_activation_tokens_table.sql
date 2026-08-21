-- One-time token dung cho link kich hoat EM-01 (UC-02) va sau nay la luong dat lai mat khau

CREATE TABLE IF NOT EXISTS activation_tokens (
    token_id      UUID PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    token_hash     VARCHAR(255) NOT NULL,
    purpose        VARCHAR(20) NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_activation_token_purpose CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET'))
);

CREATE INDEX IF NOT EXISTS idx_activation_tokens_user ON activation_tokens (user_id);
