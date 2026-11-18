package com.bnaqica.crmdemousermanagementservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bnaqica.crmdemousermanagementservice.dto.AuditRecord;
import com.bnaqica.crmdemousermanagementservice.support.AuditTestDatabase;
import com.bnaqica.crmdemousermanagementservice.support.AuditTestDatabase.AuditPostgres;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** audit-store spec, "Append-only": the writer only inserts, the reader only selects; tables are monthly partitions. */
@SpringBootTest
@Import(IntegrationTestContainers.class)
class AuditRecordRepositoryTests {

    @Autowired
    private AuditRecordRepository records;

    @Autowired
    private AuditPostgres audit;

    private Connection as(String user, String password) throws SQLException {
        return DriverManager.getConnection(audit.getJdbcUrl(), user, password);
    }

    private static AuditRecord record(UUID id) {
        return AuditRecord.builder()
                .eventId(id)
                .occurredAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .source("UMS")
                .realm("crm-internal")
                .actorId("admin-1")
                .actorType("ADMIN")
                .action("USER_DISABLED")
                .targetType("USER")
                .targetId("user-1")
                .outcome("SUCCESS")
                .ticketRef("OPS-12")
                .before("{\"status\":\"ACTIVE\"}")
                .after("{\"status\":\"DISABLED\"}")
                .build();
    }

    @Test
    void writerInsertsAndReaderFinds() {
        UUID id = UUID.randomUUID();

        assertThat(records.insert(record(id))).isTrue();

        assertThat(records.find(id)).hasValueSatisfying(found -> {
            assertThat(found.ticketRef()).isEqualTo("OPS-12");
            assertThat(found.after()).contains("DISABLED");
        });
    }

    @Test
    void sameEventIdIsStoredOnce() {
        AuditRecord record = record(UUID.randomUUID());

        assertThat(records.insert(record)).isTrue();
        assertThat(records.insert(record)).isFalse();
    }

    @Test
    void writerCannotUpdate() throws SQLException {
        records.insert(record(UUID.randomUUID()));
        try (Connection writer = as("audit_writer", AuditTestDatabase.WRITER_PASSWORD)) {
            assertThatThrownBy(
                            () -> writer.createStatement().executeUpdate("UPDATE audit.audit_record SET outcome = 'X'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void writerCannotDelete() throws SQLException {
        try (Connection writer = as("audit_writer", AuditTestDatabase.WRITER_PASSWORD)) {
            assertThatThrownBy(() -> writer.createStatement().executeUpdate("DELETE FROM audit.audit_record"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void writerCannotTouchAPartitionDirectly() throws SQLException {
        String partition =
                "audit.audit_record_" + YearMonth.now(ZoneOffset.UTC).toString().replace("-", "_");
        try (Connection writer = as("audit_writer", AuditTestDatabase.WRITER_PASSWORD)) {
            assertThatThrownBy(() -> writer.createStatement().executeUpdate("DELETE FROM " + partition))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void readerCannotInsert() throws SQLException {
        try (Connection reader = as("audit_reader", AuditTestDatabase.READER_PASSWORD)) {
            assertThatThrownBy(() -> reader.createStatement()
                            .executeUpdate("INSERT INTO audit.audit_record (event_id, occurred_at, source, action)"
                                    + " VALUES (gen_random_uuid(), now(), 'X', 'X')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void currentAndNextMonthPartitionsExist() throws SQLException {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        try (Connection reader = as("audit_reader", AuditTestDatabase.READER_PASSWORD)) {
            for (YearMonth month : new YearMonth[] {now, now.plusMonths(1)}) {
                var result = reader.createStatement()
                        .executeQuery("SELECT to_regclass('audit.audit_record_"
                                + month.toString().replace("-", "_") + "') IS NOT NULL");
                result.next();
                assertThat(result.getBoolean(1)).as(month.toString()).isTrue();
            }
        }
    }

    @Test
    void sameEventIdAtAnotherTimeIsStillStoredOnce() {
        AuditRecord first = record(UUID.randomUUID());
        AuditRecord later =
                first.toBuilder().occurredAt(first.occurredAt().plusSeconds(90)).build();

        assertThat(records.insert(first)).isTrue();
        assertThat(records.insert(later)).isFalse();

        assertThat(records.find(first.eventId()))
                .hasValueSatisfying(found -> assertThat(found.occurredAt()).isEqualTo(first.occurredAt()));
    }

    @Test
    void evenTheOwnerCannotChangeOrDeleteRecords() throws SQLException {
        records.insert(record(UUID.randomUUID()));
        try (Connection owner = as(audit.getUsername(), audit.getPassword())) {
            assertThatThrownBy(
                            () -> owner.createStatement().executeUpdate("UPDATE audit.audit_record SET outcome = 'X'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> owner.createStatement().executeUpdate("DELETE FROM audit.audit_record"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> owner.createStatement().executeUpdate("TRUNCATE audit.audit_record"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");
        }
    }

    @Test
    void readerCannotUpdateOrDelete() throws SQLException {
        try (Connection reader = as("audit_reader", AuditTestDatabase.READER_PASSWORD)) {
            assertThatThrownBy(
                            () -> reader.createStatement().executeUpdate("UPDATE audit.audit_record SET outcome = 'X'"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> reader.createStatement().executeUpdate("DELETE FROM audit.audit_record"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void recordInAMonthWithoutPartitionCreatesIt() {
        UUID id = UUID.randomUUID();
        AuditRecord farFuture = record(id).toBuilder()
                .occurredAt(Instant.parse("2040-03-15T12:00:00Z"))
                .build();

        assertThat(records.insert(farFuture)).isTrue();
        assertThat(records.find(id)).isPresent();
    }
}
