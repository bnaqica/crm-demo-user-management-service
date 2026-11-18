package com.bnaqica.crmdemousermanagementservice.processors;

import static org.assertj.core.api.Assertions.assertThat;

import com.bnaqica.crmdemousermanagementservice.service.AuditPartitions;
import com.bnaqica.crmdemousermanagementservice.support.AuditTestDatabase;
import com.bnaqica.crmdemousermanagementservice.support.AuditTestDatabase.AuditPostgres;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** The monthly partition job creates next month's audit partition ahead of time. */
@SpringBootTest
@Import(IntegrationTestContainers.class)
class AuditPartitionJobTests {

    @Autowired
    private AuditPartitions partitions;

    @Autowired
    private AuditPartitionJob job;

    @Autowired
    private AuditPostgres auditDb;

    @Autowired
    private JdbcTemplate jdbc;

    private boolean exists(String partition) throws Exception {
        try (Connection reader =
                DriverManager.getConnection(auditDb.getJdbcUrl(), "audit_reader", AuditTestDatabase.READER_PASSWORD)) {
            var result =
                    reader.createStatement().executeQuery("SELECT to_regclass('audit." + partition + "') IS NOT NULL");
            result.next();
            return result.getBoolean(1);
        }
    }

    @Test
    void nextMonthsPartitionIsCreated() throws Exception {
        assertThat(exists("audit_record_2031_06")).isFalse();

        partitions.createAhead(Instant.parse("2031-05-20T02:00:00Z"));

        assertThat(exists("audit_record_2031_06")).isTrue();
    }

    @Test
    void runningTwiceIsHarmless() throws Exception {
        partitions.createAhead(Instant.parse("2032-12-31T23:00:00Z"));
        partitions.createAhead(Instant.parse("2032-12-31T23:00:00Z"));

        assertThat(exists("audit_record_2033_01")).isTrue();
    }

    @Test
    void scheduledRunTakesTheShedLock() {
        job.run();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM crm_demo_user_management_service.shedlock WHERE name = 'audit-partitions'",
                        Integer.class))
                .isEqualTo(1);
    }
}
