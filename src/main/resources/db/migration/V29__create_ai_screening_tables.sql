-- UC-21: AI Match Analysis (Match Score, Matched/Missing Skills, AI summary) -
-- see BR-AI-01/02/03 (SRS section 3.1) and domain.AiScreeningRun/AiSkillMatch.
-- Schema kept minimal for this ticket only (no normalized skills catalog -
-- see guides/04-DATABASE_DESIGN.md mục 13 cho phần đầy đủ theo ERD gốc,
-- chưa xây vì phục vụ các UC khác chưa được giao).

CREATE TABLE IF NOT EXISTS ai_screening_runs (
    run_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id   UUID NOT NULL REFERENCES applications (id),
    -- BR-AI-02: audit trail - which model/prompt version produced this result.
    -- NULL while PENDING, or for a FAILED row that never reached the AI
    -- Engine at all (e.g. unsupported CV format - see AiScreeningService).
    model_name       VARCHAR(50),
    prompt_version   VARCHAR(20),
    match_score      NUMERIC(5, 2),
    summary          TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,
    CONSTRAINT chk_ai_screening_runs_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_ai_screening_runs_application ON ai_screening_runs (application_id, created_at DESC);
-- Used by AiScreeningDispatcher's poll query (event.OutboxDispatcher's pattern, applied here).
CREATE INDEX IF NOT EXISTS idx_ai_screening_runs_status ON ai_screening_runs (status);

CREATE TABLE IF NOT EXISTS ai_skill_matches (
    match_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id       BIGINT NOT NULL REFERENCES ai_screening_runs (run_id),
    skill_name   VARCHAR(100) NOT NULL,
    match_type   VARCHAR(20) NOT NULL,
    CONSTRAINT chk_ai_skill_matches_type CHECK (match_type IN ('MATCHED', 'MISSING'))
);

CREATE INDEX IF NOT EXISTS idx_ai_skill_matches_run ON ai_skill_matches (run_id);

-- Cache of the latest successful run's match_score, read by the Kanban card
-- Badge (BR-AI-03) without joining ai_screening_runs on every board load.
ALTER TABLE applications ADD COLUMN ai_match_score NUMERIC(5, 2);
