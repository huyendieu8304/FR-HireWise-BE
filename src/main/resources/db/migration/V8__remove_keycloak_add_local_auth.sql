-- Removes the external Keycloak dependency entirely: HireWise now manages
-- accounts itself (Spring Security + Argon2id password hashes) directly in
-- this same business database. V1-V7 are left untouched (Flyway migrations
-- already applied are immutable/checksummed) - this migration only adds the
-- new local-auth tables and evolves `users`/`job_postings` to no longer
-- depend on `keycloak_id`. The bootstrap-HR_ADMIN responsibility that V7
-- used to carry via a Flyway placeholder is replaced by
-- `config.BootstrapAdminInitializer` (a Java ApplicationRunner) - a raw SQL
-- migration cannot compute an Argon2id password hash.

-- ================== users ==================
ALTER TABLE users DROP COLUMN IF EXISTS keycloak_id;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_authenticated_at TIMESTAMPTZ;
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'INVITED';

-- ================== auth_identities ==================
-- One login method per row (LOCAL email/password, GOOGLE SSO) - see
-- domain.AuthIdentity. A user may hold more than one at once (UC-01 AF-01).
CREATE TABLE IF NOT EXISTS auth_identities (
    auth_identity_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                  BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    provider                 VARCHAR(20) NOT NULL,
    provider_subject         VARCHAR(255) NOT NULL,
    password_hash            VARCHAR(255),
    failed_login_attempts    INT NOT NULL DEFAULT 0,
    locked_until              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_auth_identity_provider CHECK (provider IN ('LOCAL', 'GOOGLE')),
    CONSTRAINT uq_auth_identity_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX IF NOT EXISTS idx_auth_identities_user ON auth_identities (user_id);

-- ================== user_sessions ==================
-- Session/refresh-token registry backing UC-01 logout and BR-AUTH-04
-- (revoke-all-sessions-on-block) - see domain.UserSession.
CREATE TABLE IF NOT EXISTS user_sessions (
    session_id            UUID PRIMARY KEY,
    user_id                BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    refresh_token_hash     VARCHAR(255) NOT NULL,
    user_agent              VARCHAR(255),
    ip_address              VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL,
    revoked_at              TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_active ON user_sessions (user_id) WHERE revoked_at IS NULL;

-- ================== activation_tokens ==================
-- One-time token backing the EM-01 activation link (UC-02) and a future
-- password-reset flow - see domain.ActivationToken.
CREATE TABLE IF NOT EXISTS activation_tokens (
    token_id     UUID PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    token_hash    VARCHAR(255) NOT NULL,
    purpose       VARCHAR(20) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_activation_token_purpose CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET'))
);

CREATE INDEX IF NOT EXISTS idx_activation_tokens_user ON activation_tokens (user_id);

-- ================== outbox_events ==================
-- Transactional outbox for reliable email delivery (activation links,
-- BR-AUTH-02 security alerts) - see domain.OutboxEvent, service.OutboxDispatcher.
CREATE TABLE IF NOT EXISTS outbox_events (
    outbox_event_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type          VARCHAR(50) NOT NULL,
    payload              TEXT NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts             INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at         TIMESTAMPTZ,
    error_message        TEXT,
    CONSTRAINT chk_outbox_event_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON outbox_events (status);

-- ================== job_postings ==================
-- created_by used to hold the Keycloak subject as free text; there is no
-- meaningful way to backfill it to a real users.user_id, so it is dropped
-- and re-added as a proper FK (nullable - this is audit-only metadata, see
-- domain.JobPosting#createdByUserId).
ALTER TABLE job_postings DROP COLUMN IF EXISTS created_by;
ALTER TABLE job_postings ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT REFERENCES users (user_id);
