-- UC-04/UC-05/UC-06: configurable recruitment pipeline (Pipeline Template + Stage).
-- See ERD 01_Core_Recruitment and BR-PIPE-01..05 (SRS section 3.1).

CREATE TABLE IF NOT EXISTS pipeline_templates (
    pipeline_template_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                    VARCHAR(150) NOT NULL,
    department_id           BIGINT REFERENCES departments (department_id),
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_pipeline_template_status CHECK (status IN ('DRAFT', 'ACTIVE'))
);

-- department_id = NULL means the template is shared company-wide (UC-04 AF-01).
COMMENT ON COLUMN pipeline_templates.department_id IS 'NULL = company-wide template, shared by every department.';

CREATE TABLE IF NOT EXISTS pipeline_stages (
    pipeline_stage_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pipeline_template_id    BIGINT NOT NULL REFERENCES pipeline_templates (pipeline_template_id),
    name                    VARCHAR(50) NOT NULL,
    code                    VARCHAR(50) NOT NULL,
    stage_type              VARCHAR(20) NOT NULL,
    position                INT NOT NULL,
    is_terminal             BOOLEAN NOT NULL DEFAULT false,
    sla_hours               INT,
    is_active               BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_pipeline_stage_type CHECK (stage_type IN
        ('INTAKE', 'SCREENING', 'INTERVIEW', 'OFFER', 'TERMINAL_SUCCESS', 'TERMINAL_REJECTED')),
    -- BR-PIPE-02: code is unique within its template, not system-wide.
    CONSTRAINT uk_pipeline_stages_template_code UNIQUE (pipeline_template_id, code)
);

CREATE INDEX IF NOT EXISTS idx_pipeline_stages_template_position ON pipeline_stages (pipeline_template_id, position);
