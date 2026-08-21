CREATE TABLE IF NOT EXISTS user_access_scopes (
    scope_id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                     BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    scope_type                  VARCHAR(20) NOT NULL, -- SYSTEM | DEPARTMENT | JOB
    department_id               BIGINT REFERENCES departments (department_id),
    job_id                      UUID REFERENCES job_postings (id),
    include_sub_departments     BOOLEAN NOT NULL DEFAULT true,
    can_write                   BOOLEAN NOT NULL DEFAULT false,
    valid_from                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to                    TIMESTAMPTZ,
    CONSTRAINT chk_scope_type CHECK (scope_type IN ('SYSTEM', 'DEPARTMENT', 'JOB'))
);

CREATE INDEX IF NOT EXISTS idx_user_access_scopes_user ON user_access_scopes (user_id);
