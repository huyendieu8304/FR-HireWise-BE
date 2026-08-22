-- UC-09/UC-10/UC-11: versioned, optionally stage-triggered email templates.
-- See BR-EMAILTPL-01..04.

CREATE TABLE IF NOT EXISTS email_templates (
    email_template_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                 VARCHAR(50) NOT NULL,
    name                 VARCHAR(150) NOT NULL,
    pipeline_stage_id    BIGINT REFERENCES pipeline_stages (pipeline_stage_id),
    subject_template     VARCHAR(255) NOT NULL,
    body_template        TEXT NOT NULL,
    version              INT NOT NULL DEFAULT 1,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- BR-EMAILTPL-01: code is unique system-wide (unlike pipeline_stages.code, which is per-template).
    CONSTRAINT uk_email_templates_code UNIQUE (code),
    CONSTRAINT chk_email_templates_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_email_templates_stage ON email_templates (pipeline_stage_id);
