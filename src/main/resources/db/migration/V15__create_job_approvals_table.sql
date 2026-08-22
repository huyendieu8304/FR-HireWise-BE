-- UC-14/UC-15: approval trail for a Job Position, kept as its own table (not a
-- single column on job_positions) so resubmission history and rejection
-- reasons are never lost - see ERD 01_Core_Recruitment.

CREATE TABLE IF NOT EXISTS job_approvals (
    job_approval_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_position_id     UUID NOT NULL REFERENCES job_positions (id),
    decision             VARCHAR(20),
    reason               TEXT,
    decided_by_user_id   BIGINT REFERENCES users (user_id),
    decided_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- BR-APR-02: decision is NULL while pending; Rejected always carries a reason.
    CONSTRAINT chk_job_approvals_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT chk_job_approvals_reject_reason CHECK (decision <> 'REJECTED' OR reason IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_job_approvals_job ON job_approvals (job_position_id, created_at);
