-- UC-17: one applications row per (candidate, job) pair - the Kanban card
-- itself - plus its immutable stage-change log. See BR-APPLY-02, BR-APPLY-04
-- and ERD 01_Core_Recruitment section 4.2/4.3.

CREATE TABLE IF NOT EXISTS applications (
    id                     UUID PRIMARY KEY,
    candidate_id           UUID NOT NULL REFERENCES candidates (id),
    job_position_id        UUID NOT NULL REFERENCES job_positions (id),
    current_stage_id       BIGINT NOT NULL REFERENCES pipeline_stages (pipeline_stage_id),
    status                 VARCHAR(20) NOT NULL DEFAULT 'NEW',
    applied_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_stage_changed_at  TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- BR-APPLY-02: at most one application per candidate per job.
    CONSTRAINT uk_applications_candidate_job UNIQUE (candidate_id, job_position_id),
    CONSTRAINT chk_applications_status CHECK (status IN
        ('NEW', 'IN_PROGRESS', 'OFFER_SENT', 'HIRED', 'REFUSED', 'WITHDRAWN'))
);

CREATE INDEX IF NOT EXISTS idx_applications_job_stage ON applications (job_position_id, current_stage_id, last_stage_changed_at);

CREATE TABLE IF NOT EXISTS application_stage_history (
    application_stage_history_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id                 UUID NOT NULL REFERENCES applications (id),
    from_stage_id                  BIGINT REFERENCES pipeline_stages (pipeline_stage_id),
    to_stage_id                    BIGINT NOT NULL REFERENCES pipeline_stages (pipeline_stage_id),
    changed_by_user_id             BIGINT REFERENCES users (user_id),
    transition_type                VARCHAR(20) NOT NULL,
    changed_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- from_stage_id is NULL for the very first event, recorded when the application is created (UC-17).
    CONSTRAINT chk_stage_history_transition_type CHECK (transition_type IN ('MANUAL', 'SYSTEM', 'ROLLBACK'))
);

CREATE INDEX IF NOT EXISTS idx_application_stage_history_app ON application_stage_history (application_id, changed_at);
