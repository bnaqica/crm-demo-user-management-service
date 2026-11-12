-- Transactional outbox: events are written in the same transaction as the change they describe, and a relay
-- publishes them to Kafka afterwards, so an event exists only if its change committed.
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(255) NOT NULL,
    message_key  VARCHAR(255) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
