-- Job postings
-- Business schema: public

CREATE TABLE IF NOT EXISTS job_postings (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by    VARCHAR(255) NOT NULL,
    department_id BIGINT REFERENCES departments(department_id),
    recruiter_id  BIGINT REFERENCES users(user_id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_job_postings_status
    ON job_postings(status);