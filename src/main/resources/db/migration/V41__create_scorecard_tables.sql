-- UC-27/UC-28: Structured Scorecard - v2 design (per team discussion, superseding the
-- original per-Job/per-Interview scoping):
--
-- scorecard_templates/scorecard_criteria: HR Admin's MASTER TEMPLATE LIBRARY - always
--   global (no job/stage scoping), reference/clone material only. Nothing ever scores
--   against these directly - see job_stage_scorecards below. Editing a Master Template
--   later never affects anything already cloned from it (no live reference kept, only
--   an optional traceability pointer).
--
-- job_stage_scorecards/job_stage_scorecard_criteria: the REAL scoring definition, scoped
--   to 1 (Job, Interview-type Stage) pair - because 1 Pipeline Template can be reused by
--   many Jobs, and the same Stage ("Technical Interview") needs completely independent
--   criteria per Job (a Backend Engineer's Technical Interview Scorecard has nothing to
--   do with a Frontend Engineer's, even sharing the same Stage row). AF-01 versioning
--   (edit-in-place vs new version) now lives here, keyed off scorecard_submissions.
--
-- scorecard_submissions/scorecard_scores: 1 evaluator's rating for 1 Interview (UC-28) -
--   now points at job_stage_scorecard_id (frozen at creation, same AF-01 guarantee as
--   before, just retargeted).

CREATE TABLE scorecard_templates (
    scorecard_template_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(255) NOT NULL,
    -- Simple soft-hide only (no versioning) - nothing ever scores against a Master
    -- Template directly, so there is no "reproducibility for old data" concern that
    -- would require versioning it the way job_stage_scorecards must be versioned.
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_by_user_id    BIGINT REFERENCES users(user_id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE scorecard_criteria (
    criterion_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scorecard_template_id UUID NOT NULL REFERENCES scorecard_templates(scorecard_template_id),
    name                  VARCHAR(255) NOT NULL,
    description           TEXT,
    weight                NUMERIC(5, 2) NOT NULL,
    max_score             NUMERIC(5, 2) NOT NULL,
    position              INT NOT NULL,
    is_required           BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE job_stage_scorecards (
    job_stage_scorecard_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id                    UUID NOT NULL REFERENCES job_positions(id),
    pipeline_stage_id         BIGINT NOT NULL REFERENCES pipeline_stages(pipeline_stage_id),
    name                      VARCHAR(255) NOT NULL,
    -- AF-01: editing a version already referenced by >=1 submission creates a new row
    -- (version = previous + 1) instead of overwriting - see JobStageScorecardService.
    version                   INT NOT NULL DEFAULT 1,
    status                    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    -- Traceability only ("cloned from this Master Template as a starting point") - never
    -- read back for behavior, so editing/archiving the Master afterwards is always safe.
    source_master_template_id UUID REFERENCES scorecard_templates(scorecard_template_id),
    created_by_user_id        BIGINT REFERENCES users(user_id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE job_stage_scorecard_criteria (
    criterion_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_stage_scorecard_id UUID NOT NULL REFERENCES job_stage_scorecards(job_stage_scorecard_id),
    name                   VARCHAR(255) NOT NULL,
    description            TEXT,
    weight                 NUMERIC(5, 2) NOT NULL,
    max_score              NUMERIC(5, 2) NOT NULL,
    position               INT NOT NULL,
    is_required            BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE scorecard_submissions (
    submission_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id           UUID NOT NULL REFERENCES interviews(interview_id),
    evaluator_id           BIGINT NOT NULL REFERENCES users(user_id),
    job_stage_scorecard_id UUID NOT NULL REFERENCES job_stage_scorecards(job_stage_scorecard_id),
    overall_comment        TEXT,
    weighted_score         NUMERIC(5, 2),
    -- DRAFT = evaluator has started/saved progress but not pressed "Gui danh gia" yet;
    -- SUBMITTED = final (BR-SCORE-01 already satisfied at that point).
    status                 VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                           CHECK (status IN ('DRAFT', 'SUBMITTED')),
    submitted_at           TIMESTAMPTZ,
    -- BR-SCORE-03: set by ScorecardLockWorker ~24h after the interview's start time;
    -- NULL = still editable. Cleared only by the HR Admin-only unlock action (audited).
    locked_at              TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 1 evaluator scores a given Interview at most once (their draft is reused/updated,
    -- never duplicated) - also what ScorecardSubmissionRepository upserts against.
    UNIQUE (interview_id, evaluator_id)
);

CREATE TABLE scorecard_scores (
    score_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL REFERENCES scorecard_submissions(submission_id),
    criterion_id  UUID NOT NULL REFERENCES job_stage_scorecard_criteria(criterion_id),
    score         NUMERIC(5, 2),
    comment       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (submission_id, criterion_id)
);

-- Which (Job, Interview-type Stage) an already-scheduled Interview instance is FOR,
-- captured once at schedule time (InterviewService#scheduleInterview already knows the
-- exact target Stage right when it creates the row) - this is what anchors an Interview
-- permanently to the correct Scorecard, independent of wherever the Application's
-- CURRENT stage may have moved on to by the time someone actually opens the Scorecard
-- tab to score it. Nullable because existing Interview rows predate this column and
-- simply won't have a working Scorecard tab (acceptable for pre-existing dev data).
ALTER TABLE interviews ADD COLUMN pipeline_stage_id BIGINT REFERENCES pipeline_stages(pipeline_stage_id);

CREATE INDEX idx_scorecard_criteria_template_id ON scorecard_criteria(scorecard_template_id);
CREATE INDEX idx_job_stage_scorecards_job_stage ON job_stage_scorecards(job_id, pipeline_stage_id);
CREATE INDEX idx_job_stage_scorecard_criteria_parent ON job_stage_scorecard_criteria(job_stage_scorecard_id);
CREATE INDEX idx_scorecard_submissions_interview_id ON scorecard_submissions(interview_id);
CREATE INDEX idx_scorecard_submissions_jss_id ON scorecard_submissions(job_stage_scorecard_id);
CREATE INDEX idx_scorecard_scores_submission_id ON scorecard_scores(submission_id);
CREATE INDEX idx_interviews_pipeline_stage_id ON interviews(pipeline_stage_id);

-- New fine-grained permission: BR-SCORE-03 "sau khi khoa, chi HR Admin moi co quyen
-- mo khoa dac biet" - narrower than SCORECARD_TEMPLATE_MANAGE, which Hiring Manager
-- also holds, so reusing that code would incorrectly let Hiring Manager unlock too.
INSERT INTO permissions (code, description, is_write) VALUES
    ('SCORECARD_UNLOCK', 'Unlock a locked Scorecard Submission for editing (HR Admin only)', true)
    ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r JOIN permissions p ON (
    r.code = 'HR_ADMIN' AND p.code = 'SCORECARD_UNLOCK'
)
ON CONFLICT DO NOTHING;
