-- Mot dong = mot phuong thuc dang nhap (LOCAL email/password, GOOGLE SSO) -
-- xem domain.AuthIdentity. Mot user co the giu nhieu hon 1 dong cung luc.
-- Tach rieng khoi users de danh tinh noi bo (users) khong
-- phu thuoc vao cach dang nhap, va de mo rong them provider khac sau nay
-- ma khong phai dong cham toi bang users.

CREATE TABLE IF NOT EXISTS auth_identities (
    auth_identity_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                  BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    provider                 VARCHAR(20) NOT NULL,
    provider_subject         VARCHAR(255) NOT NULL,
    password_hash            VARCHAR(255),
    failed_login_attempts    INT NOT NULL DEFAULT 0,
    locked_until             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_auth_identity_provider CHECK (provider IN ('LOCAL', 'GOOGLE')),
    CONSTRAINT uq_auth_identity_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX IF NOT EXISTS idx_auth_identities_user ON auth_identities (user_id);
