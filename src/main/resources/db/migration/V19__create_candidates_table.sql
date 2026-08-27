-- UC-17: a candidate profile is system-wide, independent of any single Job -
-- see ERD 01_Core_Recruitment. primary_email is unique so a repeat applicant
-- (BR-APPLY-02) reuses their existing candidates row instead of duplicating it.

CREATE TABLE IF NOT EXISTS candidates (
    id              UUID PRIMARY KEY,
    full_name       VARCHAR(150) NOT NULL,
    primary_email   VARCHAR(255) NOT NULL,
    phone           VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_candidates_primary_email UNIQUE (primary_email),
    CONSTRAINT chk_candidates_status CHECK (status IN ('ACTIVE', 'BLACKLISTED'))
);
