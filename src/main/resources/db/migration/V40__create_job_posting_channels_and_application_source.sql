-- UC-31/UC-32 (ban sua doi): theo doi hieu qua chia se tin tuyen dung.
--
-- Vi khong con goi API dang bai bat dong bo, khong con trang thai trung gian
-- Processing/Success/Failed cua LV-31. Thay vao do moi cap (Job, Channel) la
-- MOT dong tong hop dem so lan chia se va so luot click - dung tinh than
-- BR-POST-02 (1 ban ghi duy nhat, chia se lai thi cap nhat tai cho).

CREATE TABLE IF NOT EXISTS job_posting_channels (
    job_posting_channel_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_position_id         UUID   NOT NULL REFERENCES job_positions (id),
    publishing_channel_id   BIGINT NOT NULL REFERENCES publishing_channels (publishing_channel_id),
    -- So lan Recruiter bam [Chia se] tren kenh nay.
    share_count             INT    NOT NULL DEFAULT 0,
    -- So luot nguoi that mo link chia se. Crawler (facebookexternalhit,
    -- LinkedInBot...) duoc loc ra o PublicJobShareController, khong dem vao day.
    click_count             INT    NOT NULL DEFAULT 0,
    first_shared_at         TIMESTAMPTZ,
    last_shared_at          TIMESTAMPTZ,
    last_shared_by_user_id  BIGINT REFERENCES users (user_id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- BR-POST-02: 1 ban ghi duy nhat moi cap (Job, Channel).
    CONSTRAINT uk_job_posting_channels_job_channel UNIQUE (job_position_id, publishing_channel_id)
);

CREATE INDEX IF NOT EXISTS idx_job_posting_channels_job ON job_posting_channels (job_position_id);

-- UC-32: quy ung vien ve dung kenh da mang ho toi. Gia tri lay tu utm_source
-- tren link chia se, doi chieu voi publishing_channels.utm_source. NULL =
-- ung vien vao thang Job Board, khong qua link chia se nao.
ALTER TABLE applications ADD COLUMN source VARCHAR(50);

COMMENT ON COLUMN applications.source IS
    'UC-32: utm_source cua link chia se da dan ung vien toi (khop publishing_channels.utm_source). NULL = truy cap truc tiep.';

CREATE INDEX IF NOT EXISTS idx_applications_job_source ON applications (job_position_id, source);
