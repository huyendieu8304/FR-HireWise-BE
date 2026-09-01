-- UC-29/UC-30 (M14 - Candidate Rejection). See ERD 01_Core_Recruitment
-- section 2 (rejection_reasons / application_rejections) and BR-REJ-01..03.
--
-- rejection_reasons: standardized catalog (BR-REJ-01) so a Recruiter always
-- picks from a fixed list instead of free-typing a reason - keeps reporting
-- meaningful across every rejection in the system.
--
-- application_rejections: one append-style event row per Application that
-- was actually rejected (who did it, which standardized reason, optional
-- custom message). Kept as its own table rather than columns on
-- `applications` - same reasoning as `job_approvals` (see V15) - and
-- UNIQUE(application_id) enforces BR-REJ-03 (Refused is terminal; no
-- revert, no re-reject - reconsidering a candidate means a new Application).

CREATE TABLE IF NOT EXISTS rejection_reasons (
    reason_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(50) NOT NULL,
    label        VARCHAR(150) NOT NULL,
    category     VARCHAR(30) NOT NULL,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_rejection_reasons_code UNIQUE (code),
    CONSTRAINT chk_rejection_reasons_category CHECK (category IN
        ('SKILL_GAP', 'CULTURE_GAP', 'SALARY_GAP', 'DUPLICATE', 'WITHDRAWN', 'OTHER'))
);

CREATE TABLE IF NOT EXISTS application_rejections (
    rejection_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id         UUID NOT NULL REFERENCES applications (id),
    reason_id              BIGINT NOT NULL REFERENCES rejection_reasons (reason_id),
    rejected_by_user_id    BIGINT REFERENCES users (user_id),
    custom_message         TEXT,
    rejected_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- BR-REJ-03: terminal, no revert - at most one rejection record per Application ever.
    CONSTRAINT uk_application_rejections_application UNIQUE (application_id)
);

CREATE INDEX IF NOT EXISTS idx_application_rejections_reason ON application_rejections (reason_id);

-- BR-REJ-01: standardized catalog - examples named in ERD 01_Core_Recruitment
-- ("Technical Gap, Culture Gap, Salary Gap, Duplicate, Withdrawn").
INSERT INTO rejection_reasons (code, label, category) VALUES
    ('TECHNICAL_GAP', 'Ky nang chuyen mon chua phu hop', 'SKILL_GAP'),
    ('CULTURE_GAP', 'Chua phu hop van hoa cong ty', 'CULTURE_GAP'),
    ('SALARY_GAP', 'Muc luong ky vong khong phu hop', 'SALARY_GAP'),
    ('DUPLICATE', 'Ho so trung lap voi ung vien khac', 'DUPLICATE'),
    ('WITHDRAWN', 'Ung vien da rut ho so / khong phan hoi', 'WITHDRAWN'),
    ('OTHER', 'Ly do khac', 'OTHER')
ON CONFLICT (code) DO NOTHING;
