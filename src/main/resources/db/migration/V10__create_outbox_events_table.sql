-- Transactional outbox cho gui email, ghi dong nay trong CUNG transaction voi thay doi nghiep
-- vu gay ra no (vd tao user), roi mot poller rieng (event.OutboxDispatcher) nhat PENDING va gui bat dong bo - xem event.OutboxEvent.

CREATE TABLE IF NOT EXISTS outbox_events (
    outbox_event_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type          VARCHAR(50) NOT NULL,
    payload              TEXT NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts             INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at         TIMESTAMPTZ,
    error_message        TEXT,
    CONSTRAINT chk_outbox_event_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON outbox_events (status);
