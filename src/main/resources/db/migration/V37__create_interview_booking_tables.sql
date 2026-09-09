-- UC-25, UC-34, UC-35: Self-service Interview Booking
CREATE TABLE IF NOT EXISTS interview_booking_requests (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id    UUID NOT NULL REFERENCES applications (id),
    interviewer_id    BIGINT NOT NULL REFERENCES users (user_id),
    date_range_start  DATE NOT NULL,
    date_range_end    DATE NOT NULL,
    booking_token     UUID NOT NULL UNIQUE,
    expires_at        TIMESTAMPTZ NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    target_stage_id   BIGINT REFERENCES pipeline_stages (pipeline_stage_id),
    mode              VARCHAR(20),
    location_or_link  TEXT,
    created_by        BIGINT NOT NULL REFERENCES users (user_id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_booking_requests_status CHECK (status IN ('OPEN', 'COMPLETED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_booking_requests_token ON interview_booking_requests (booking_token);
CREATE INDEX IF NOT EXISTS idx_booking_requests_app ON interview_booking_requests (application_id);

CREATE TABLE IF NOT EXISTS interview_booking_slots (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_request_id  BIGINT NOT NULL REFERENCES interview_booking_requests (id) ON DELETE CASCADE,
    slot_date           DATE NOT NULL,
    slot_time           TIME NOT NULL,
    duration_minutes    INT NOT NULL DEFAULT 45,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    held_until          TIMESTAMPTZ,
    selected_at         TIMESTAMPTZ,
    CONSTRAINT chk_booking_slots_status CHECK (status IN ('OPEN', 'HELD', 'CONFIRMED'))
);

CREATE INDEX IF NOT EXISTS idx_booking_slots_request ON interview_booking_slots (booking_request_id);
