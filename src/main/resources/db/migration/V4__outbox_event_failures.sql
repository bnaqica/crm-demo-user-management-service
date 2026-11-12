-- An event Kafka keeps refusing is retried a few times, then parked so it stops blocking the events behind it.
ALTER TABLE outbox_event
    ADD COLUMN attempts   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN last_error TEXT,
    ADD COLUMN parked_at  TIMESTAMPTZ;

DROP INDEX idx_outbox_event_unpublished;
CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL AND parked_at IS NULL;
