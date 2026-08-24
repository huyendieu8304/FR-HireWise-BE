-- UC-12/UC-13: extends job_positions (created in V8) with the fields needed to
-- draft, submit and pipeline a Job Position. Only additive ALTERs here - V8 is
-- left untouched since it may already have shipped.

ALTER TABLE job_positions
    ADD COLUMN employment_type       VARCHAR(20),
    ADD COLUMN salary_min            NUMERIC(14, 2),
    ADD COLUMN salary_max            NUMERIC(14, 2),
    ADD COLUMN openings              INT NOT NULL DEFAULT 1,
    ADD COLUMN application_deadline  DATE,
    ADD COLUMN requirements          TEXT,
    ADD COLUMN benefits              TEXT,
    ADD COLUMN hiring_manager_id     BIGINT REFERENCES users (user_id),
    ADD COLUMN pipeline_template_id  BIGINT REFERENCES pipeline_templates (pipeline_template_id);

-- BR-JOB-02: salary_min <= salary_max when both are provided ("Thoa thuan" = both NULL).
ALTER TABLE job_positions
    ADD CONSTRAINT chk_job_positions_salary_range
        CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max);

ALTER TABLE job_positions
    ADD CONSTRAINT chk_job_positions_employment_type
        CHECK (employment_type IS NULL OR employment_type IN ('FULL_TIME', 'PART_TIME', 'INTERNSHIP', 'CONTRACT'));

COMMENT ON COLUMN job_positions.openings IS 'So luong chi tieu (BR-JOB-01: bat buoc, >= 1).';
