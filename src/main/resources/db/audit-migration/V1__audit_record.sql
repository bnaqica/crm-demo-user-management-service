-- The central audit store: one append-only table, partitioned by month on occurred_at. Run by audit_owner.
-- ${writer} may only INSERT (and create next month's partition through ensure_partition); ${reader} may only SELECT.
CREATE TABLE audit.audit_record (
    event_id       UUID         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    source         VARCHAR(32)  NOT NULL,      -- KEYCLOAK, UMS, CRM, PORTAL
    realm          VARCHAR(64),
    actor_id       VARCHAR(255),
    actor_type     VARCHAR(32),               -- ADMIN, USER, SYSTEM
    action         VARCHAR(128) NOT NULL,
    target_type    VARCHAR(64),
    target_id      VARCHAR(320),
    outcome        VARCHAR(16),               -- SUCCESS or FAILURE
    ticket_ref     VARCHAR(64),
    before         JSONB,
    after          JSONB,
    ip_address     VARCHAR(64),
    correlation_id VARCHAR(255),
    reason         TEXT,
    PRIMARY KEY (event_id, occurred_at)       -- one row per event id; the partition key has to be in it
) PARTITION BY RANGE (occurred_at);

CREATE INDEX idx_audit_record_occurred_at ON audit.audit_record (occurred_at);
CREATE INDEX idx_audit_record_actor ON audit.audit_record (actor_id, occurred_at);
CREATE INDEX idx_audit_record_target ON audit.audit_record (target_id, occurred_at);
CREATE INDEX idx_audit_record_ticket ON audit.audit_record (ticket_ref, occurred_at);
CREATE INDEX idx_audit_record_correlation ON audit.audit_record (correlation_id, occurred_at);

-- Creates the partition for the month containing month_of (UTC) if it is missing. Runs with the owner's rights, so
-- the writer can call it without being able to change tables.
CREATE FUNCTION audit.ensure_partition(month_of DATE) RETURNS VOID
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = audit, pg_temp AS
$$
DECLARE
    first_day DATE := date_trunc('month', month_of)::DATE;
    partition TEXT := 'audit_record_' || to_char(first_day, 'YYYY_MM');
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS audit.%I PARTITION OF audit.audit_record FOR VALUES FROM (%L) TO (%L)',
        partition,
        first_day::TEXT || ' 00:00:00+00',
        (first_day + INTERVAL '1 month')::DATE::TEXT || ' 00:00:00+00');
END
$$;

REVOKE ALL ON FUNCTION audit.ensure_partition(DATE) FROM PUBLIC;

SELECT audit.ensure_partition((now() AT TIME ZONE 'UTC')::DATE);
SELECT audit.ensure_partition(((now() AT TIME ZONE 'UTC') + INTERVAL '1 month')::DATE);

GRANT USAGE ON SCHEMA audit TO ${writer}, ${reader};
GRANT INSERT ON audit.audit_record TO ${writer};
GRANT EXECUTE ON FUNCTION audit.ensure_partition(DATE) TO ${writer};
GRANT SELECT ON audit.audit_record TO ${reader};
