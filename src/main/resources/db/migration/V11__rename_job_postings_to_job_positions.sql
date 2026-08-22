-- Renames the job_postings table (and its dependent index) to job_positions,
-- to match the JobPosition domain entity.

ALTER TABLE job_postings RENAME TO job_positions;

ALTER INDEX idx_job_postings_status RENAME TO idx_job_positions_status;
