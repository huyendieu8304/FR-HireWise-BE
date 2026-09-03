-- UC-24: Create interviews and interview_participants tables.
-- interviews: one row per "lên lịch phỏng vấn" action by a Recruiter.
-- interview_participants: join table between interviews and interviewers (users).

CREATE TABLE interviews (
    interview_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   UUID        NOT NULL REFERENCES applications(id),
    scheduled_by     BIGINT      NOT NULL REFERENCES users(user_id),
    interview_date   DATE        NOT NULL,
    interview_time   TIME        NOT NULL,
    -- ONLINE = virtual meeting; ONSITE = in-person
    mode             VARCHAR(20) NOT NULL CHECK (mode IN ('ONLINE', 'ONSITE')),
    -- Meeting URL for ONLINE, physical address for ONSITE; optional.
    location_or_link TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                     CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE interview_participants (
    id              BIGSERIAL PRIMARY KEY,
    interview_id    UUID   NOT NULL REFERENCES interviews(interview_id) ON DELETE CASCADE,
    interviewer_id  BIGINT NOT NULL REFERENCES users(user_id),
    created_at      TIMESTAMPTZ NOT NULL,
    -- One interviewer per interview exactly once (BR-SCHED-02).
    UNIQUE (interview_id, interviewer_id)
);

CREATE INDEX idx_interviews_application_id ON interviews(application_id);
CREATE INDEX idx_interview_participants_interview_id ON interview_participants(interview_id);
