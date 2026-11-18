package com.bnaqica.crmdemousermanagementservice.repository;

import com.bnaqica.crmdemousermanagementservice.config.AuditDatabase;
import com.bnaqica.crmdemousermanagementservice.dto.AuditRecord;
import com.bnaqica.crmdemousermanagementservice.dto.AuditSearchCriteria;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

/** {@code audit.audit_record} in the audit database: inserts as the writer, reads as the reader. No updates or deletes. */
@Repository
@RequiredArgsConstructor
public class AuditRecordRepository {

    private static final String INSERT = """
            WITH claimed AS (
                INSERT INTO audit.audit_event_id (event_id, occurred_at) VALUES (:eventId, :occurredAt)
                ON CONFLICT DO NOTHING
                RETURNING event_id)
            INSERT INTO audit.audit_record (event_id, occurred_at, source, realm, actor_id, actor_type, action,
                target_type, target_id, outcome, ticket_ref, before, after, ip_address, correlation_id, reason)
            SELECT :eventId, :occurredAt, :source, :realm, :actorId, :actorType, :action, :targetType, :targetId,
                :outcome, :ticketRef, CAST(:before AS jsonb), CAST(:after AS jsonb), :ipAddress, :correlationId, :reason
            FROM claimed
            ON CONFLICT DO NOTHING
            """;

    static final String COLUMNS = "event_id, occurred_at, source, realm, actor_id, actor_type, action, target_type,"
            + " target_id, outcome, ticket_ref, before::text AS before, after::text AS after, ip_address,"
            + " correlation_id, reason";

    private final AuditDatabase database;

    /** @return false if a record with this event id exists already, whatever its time */
    public boolean insert(AuditRecord record) {
        try {
            return doInsert(record);
        } catch (DataAccessException ex) {
            if (!isMissingPartition(ex)) {
                throw ex;
            }
            ensurePartition(record.occurredAt().atZone(ZoneOffset.UTC).toLocalDate());
            return doInsert(record);
        }
    }

    private boolean doInsert(AuditRecord record) {
        return database.writer()
                        .sql(INSERT)
                        .param("eventId", record.eventId())
                        .param("occurredAt", Timestamp.from(record.occurredAt()))
                        .param("source", record.source())
                        .param("realm", record.realm())
                        .param("actorId", record.actorId())
                        .param("actorType", record.actorType())
                        .param("action", record.action())
                        .param("targetType", record.targetType())
                        .param("targetId", record.targetId())
                        .param("outcome", record.outcome())
                        .param("ticketRef", record.ticketRef())
                        .param("before", record.before())
                        .param("after", record.after())
                        .param("ipAddress", record.ipAddress())
                        .param("correlationId", record.correlationId())
                        .param("reason", record.reason())
                        .update()
                == 1;
    }

    /** Creates the monthly partition holding {@code day} if it is missing. */
    public void ensurePartition(java.time.LocalDate day) {
        database.writer()
                .sql("SELECT audit.ensure_partition(:day)")
                .param("day", java.sql.Date.valueOf(day))
                .query((rs, row) -> 1)
                .list();
    }

    public Optional<AuditRecord> find(UUID eventId) {
        return database.reader()
                .sql("SELECT " + COLUMNS + " FROM audit.audit_record WHERE event_id = :id ORDER BY occurred_at LIMIT 1")
                .param("id", eventId)
                .query(AuditRecordRepository::map)
                .optional();
    }

    /** Records matching {@code criteria}, newest first. */
    public List<AuditRecord> search(AuditSearchCriteria criteria, int page, int size) {
        Where where = where(criteria);
        var statement = database.reader()
                .sql("SELECT " + COLUMNS + " FROM audit.audit_record" + where.sql()
                        + " ORDER BY occurred_at DESC, event_id LIMIT :limit OFFSET :offset")
                .param("limit", size)
                .param("offset", (long) page * size);
        where.params().forEach(statement::param);
        return statement.query(AuditRecordRepository::map).list();
    }

    public long count(AuditSearchCriteria criteria) {
        Where where = where(criteria);
        var statement = database.reader().sql("SELECT count(*) FROM audit.audit_record" + where.sql());
        where.params().forEach(statement::param);
        return statement.query(Long.class).single();
    }

    private static Where where(AuditSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(" WHERE occurred_at >= :from AND occurred_at < :to");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", Timestamp.from(criteria.from()));
        params.put("to", Timestamp.from(criteria.to()));
        equal(sql, params, "source", criteria.source());
        equal(sql, params, "realm", criteria.realm());
        equal(sql, params, "actor_id", criteria.actorId());
        equal(sql, params, "target_id", criteria.targetId());
        equal(sql, params, "action", criteria.action());
        equal(sql, params, "outcome", criteria.outcome());
        equal(sql, params, "ticket_ref", criteria.ticketRef());
        equal(sql, params, "correlation_id", criteria.correlationId());
        return new Where(sql.toString(), params);
    }

    /** Column names are constants from {@link #where}; values are always bound parameters. */
    private static void equal(StringBuilder sql, Map<String, Object> params, String column, String value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = :").append(column);
            params.put(column, value);
        }
    }

    private record Where(String sql, Map<String, Object> params) {}

    static AuditRecord map(ResultSet rs, int row) throws SQLException {
        return AuditRecord.builder()
                .eventId(rs.getObject("event_id", UUID.class))
                .occurredAt(rs.getTimestamp("occurred_at").toInstant())
                .source(rs.getString("source"))
                .realm(rs.getString("realm"))
                .actorId(rs.getString("actor_id"))
                .actorType(rs.getString("actor_type"))
                .action(rs.getString("action"))
                .targetType(rs.getString("target_type"))
                .targetId(rs.getString("target_id"))
                .outcome(rs.getString("outcome"))
                .ticketRef(rs.getString("ticket_ref"))
                .before(rs.getString("before"))
                .after(rs.getString("after"))
                .ipAddress(rs.getString("ip_address"))
                .correlationId(rs.getString("correlation_id"))
                .reason(rs.getString("reason"))
                .build();
    }

    /**
     * PostgreSQL's "no partition of relation found for row" is SQLState 23514 (check violation); the table has no
     * other checks, so the state alone tells it apart, whatever the server's message language.
     */
    private static boolean isMissingPartition(DataAccessException ex) {
        return ex.getMostSpecificCause() instanceof SQLException sql && "23514".equals(sql.getSQLState());
    }
}
