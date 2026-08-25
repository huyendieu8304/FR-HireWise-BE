-- UC-16: the public Job Board list/detail show a work location per job
-- ("... kem Chuc danh, Phong ban, Loai hinh, Dia diem." - Normal Flow step
-- 2), which job_positions did not have a column for yet. Additive ALTER
-- only, same spirit as V14.

ALTER TABLE job_positions
    ADD COLUMN location VARCHAR(255);

COMMENT ON COLUMN job_positions.location IS 'Work location shown on the public Job Board (UC-16), e.g. "Ho Chi Minh". NULL = not specified.';
