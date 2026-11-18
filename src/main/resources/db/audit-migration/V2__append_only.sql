-- At most one record per event id, whatever its occurred_at: the partitioned table's key has to include the time, so
-- ids are claimed here first, in the same statement as the insert.
CREATE TABLE audit.audit_event_id (
    event_id    UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL
);

INSERT INTO audit.audit_event_id (event_id, occurred_at)
SELECT event_id, min(occurred_at) FROM audit.audit_record GROUP BY event_id;

GRANT INSERT ON audit.audit_event_id TO ${writer};
GRANT SELECT (event_id) ON audit.audit_event_id TO ${writer};   -- for INSERT ... RETURNING event_id

-- Append-only even for the owner: changing or removing records raises an error. (The owner could still drop these
-- triggers, so its credentials belong to migrations only.)
CREATE FUNCTION audit.refuse_change() RETURNS TRIGGER LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit records are append-only (% on %)', TG_OP, TG_TABLE_NAME;
END
$$;

CREATE TRIGGER audit_record_append_only BEFORE UPDATE OR DELETE ON audit.audit_record
    FOR EACH ROW EXECUTE FUNCTION audit.refuse_change();
CREATE TRIGGER audit_record_no_truncate BEFORE TRUNCATE ON audit.audit_record
    FOR EACH STATEMENT EXECUTE FUNCTION audit.refuse_change();
CREATE TRIGGER audit_event_id_append_only BEFORE UPDATE OR DELETE ON audit.audit_event_id
    FOR EACH ROW EXECUTE FUNCTION audit.refuse_change();
CREATE TRIGGER audit_event_id_no_truncate BEFORE TRUNCATE ON audit.audit_event_id
    FOR EACH STATEMENT EXECUTE FUNCTION audit.refuse_change();
