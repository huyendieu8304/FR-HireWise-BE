-- Cross-cutting audit trail (ERD 05_Auth_RBAC_Integrations). First consumer
-- is UC-07/UC-08 (Connect/Reconnect/Disconnect Cloud Storage), but the table
-- is generic so later use cases (role assignment, job publish, etc.) can
-- write to it too instead of each inventing its own log shape.

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_log_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id    BIGINT REFERENCES users (user_id),
    action           VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        VARCHAR(64),
    before_json      TEXT,
    after_json       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs (actor_user_id, created_at);
