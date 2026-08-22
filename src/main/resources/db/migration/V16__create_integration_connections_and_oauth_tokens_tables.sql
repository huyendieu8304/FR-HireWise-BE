-- UC-07/UC-08: generic 3rd-party OAuth connection metadata (reused later by
-- Calendar/Social integrations, UC-18/UC-19) plus its encrypted token, kept in
-- a separate table so ordinary application code never has a reason to select
-- token columns - see ERD 05_Auth_RBAC_Integrations and BR-STORAGE-01.

CREATE TABLE IF NOT EXISTS integration_connections (
    integration_connection_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider                   VARCHAR(30) NOT NULL,
    purpose                    VARCHAR(30) NOT NULL,
    account_label              VARCHAR(255),
    status                     VARCHAR(20) NOT NULL DEFAULT 'CONNECTED',
    created_by_user_id         BIGINT REFERENCES users (user_id),
    connected_at               TIMESTAMPTZ,
    token_expires_at           TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_integration_connections_status CHECK (status IN ('CONNECTED', 'EXPIRED', 'REVOKED'))
);

CREATE TABLE IF NOT EXISTS oauth_tokens (
    oauth_token_id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    integration_connection_id   BIGINT NOT NULL REFERENCES integration_connections (integration_connection_id),
    access_token_encrypted      TEXT NOT NULL,
    refresh_token_encrypted     TEXT,
    token_type                  VARCHAR(20) NOT NULL DEFAULT 'Bearer',
    expires_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- one active token row per connection; reconnect (UC-08 AF-01) replaces it in place.
    CONSTRAINT uk_oauth_tokens_connection UNIQUE (integration_connection_id)
);

COMMENT ON COLUMN oauth_tokens.access_token_encrypted IS 'BR-STORAGE-01: encrypted at rest, never returned by any read API.';
