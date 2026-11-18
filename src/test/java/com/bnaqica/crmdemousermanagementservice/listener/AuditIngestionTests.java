package com.bnaqica.crmdemousermanagementservice.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bnaqica.crmdemousermanagementservice.dto.AuditRecord;
import com.bnaqica.crmdemousermanagementservice.repository.AuditRecordRepository;
import com.bnaqica.crmdemousermanagementservice.support.AuditTestDatabase;
import com.bnaqica.crmdemousermanagementservice.support.AuditTestDatabase.AuditPostgres;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.TestKafka;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

/** audit-store spec: events from audit-events and keycloak-events land in the audit database, once, without secrets. */
@SpringBootTest
@Import(IntegrationTestContainers.class)
class AuditIngestionTests {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private AuditRecordRepository records;

    @Autowired
    private AuditPostgres auditDb;

    @Autowired
    private KafkaContainer kafka;

    private static String umsEvent(UUID id, Instant at) {
        return """
                {"eventId":"%s","schemaVersion":1,"occurredAt":"%s","source":"UMS","realm":"crm-internal",
                 "actorId":"admin-1","actorType":"ADMIN","action":"USER_DISABLED","targetType":"USER",
                 "targetId":"user-1","outcome":"SUCCESS","ticketRef":"OPS-12",
                 "before":{"status":"ACTIVE"},"after":{"status":"DISABLED"},
                 "ipAddress":"10.0.0.5","correlationId":"corr-1","reason":null}""".formatted(id, at);
    }

    private AuditRecord awaitRecord(UUID id) {
        await().atMost(WAIT).until(() -> records.find(id).isPresent());
        return records.find(id).orElseThrow();
    }

    private int rows(UUID id) throws Exception {
        try (Connection reader =
                DriverManager.getConnection(auditDb.getJdbcUrl(), "audit_reader", AuditTestDatabase.READER_PASSWORD)) {
            var result = reader.createStatement()
                    .executeQuery("SELECT count(*) FROM audit.audit_record WHERE event_id = '" + id + "'");
            result.next();
            return result.getInt(1);
        }
    }

    @Test
    void umsRecordStored() {
        UUID id = UUID.randomUUID();

        kafkaTemplate.send("audit-events", "user-1", umsEvent(id, Instant.now()));

        AuditRecord record = awaitRecord(id);
        assertThat(record.source()).isEqualTo("UMS");
        assertThat(record.action()).isEqualTo("USER_DISABLED");
        assertThat(record.ticketRef()).isEqualTo("OPS-12");
        assertThat(record.actorType()).isEqualTo("ADMIN");
        assertThat(record.before()).contains("ACTIVE");
        assertThat(record.after()).contains("DISABLED");
        assertThat(record.ipAddress()).isEqualTo("10.0.0.5");
        assertThat(record.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void redeliveredEventStoredOnce() throws Exception {
        UUID id = UUID.randomUUID();
        String event = umsEvent(id, Instant.now().truncatedTo(ChronoUnit.MILLIS));

        kafkaTemplate.send("audit-events", "user-1", event);
        kafkaTemplate.send("audit-events", "user-1", event);

        awaitRecord(id);
        Thread.sleep(3000); // let the second delivery be handled too
        assertThat(rows(id)).isEqualTo(1);
    }

    @Test
    void failedLoginFromKeycloakStored() {
        String keycloakId = UUID.randomUUID().toString();
        kafkaTemplate.send(
                "keycloak-events", "u", """
                {"id":"%s","time":%d,"realm":"crm-internal","type":"LOGIN_ERROR","userId":"u-1",
                 "error":"invalid_user_credentials","details":{"username":"ana@corp.example","ipAddress":"10.0.0.9"}}""".formatted(keycloakId, Instant.now().toEpochMilli()));

        AuditRecord record = awaitRecord(UUID.fromString(keycloakId));
        assertThat(record.source()).isEqualTo("KEYCLOAK");
        assertThat(record.realm()).isEqualTo("crm-internal");
        assertThat(record.action()).isEqualTo("LOGIN_ERROR");
        assertThat(record.outcome()).isEqualTo("FAILURE");
        assertThat(record.actorId()).isEqualTo("u-1");
        assertThat(record.ipAddress()).isEqualTo("10.0.0.9");
    }

    @Test
    void keycloakEventWithANonUuidIdGetsAStableId() {
        String keycloakId = "kc-" + UUID.randomUUID();
        kafkaTemplate.send(
                "keycloak-events", "u", """
                {"id":"%s","time":%d,"realm":"portal","type":"LOGIN","userId":"u-2","details":{}}""".formatted(keycloakId, Instant.now().toEpochMilli()));

        AuditRecord record =
                awaitRecord(UUID.nameUUIDFromBytes(("keycloak-event:" + keycloakId).getBytes(StandardCharsets.UTF_8)));
        assertThat(record.outcome()).isEqualTo("SUCCESS");
    }

    @Test
    void adminEventStoredWithoutTheSecret() {
        String keycloakId = UUID.randomUUID().toString();
        kafkaTemplate.send(
                "keycloak-events", "u", """
                {"id":"%s","time":%d,"realm":"portal","kind":"ADMIN","operationType":"UPDATE","resourceType":"USER",
                 "resourcePath":"users/u-3","authDetails":{"userId":"svc-1","ipAddress":"10.0.0.7"},
                 "representation":"{\\"username\\":\\"pat\\",\\"credentials\\":[{\\"type\\":\\"password\\",\\"value\\":\\"S3cret-pass!\\"}],\\"attributes\\":{\\"phone\\":[\\"555\\"]}}"}""".formatted(keycloakId, Instant.now().toEpochMilli()));

        AuditRecord record = awaitRecord(UUID.fromString(keycloakId));
        assertThat(record.action()).isEqualTo("ADMIN_USER_UPDATE");
        assertThat(record.actorType()).isEqualTo("ADMIN");
        assertThat(record.actorId()).isEqualTo("svc-1");
        assertThat(record.targetType()).isEqualTo("USER");
        assertThat(record.targetId()).isEqualTo("u-3");
        assertThat(record.after())
                .contains("pat")
                .doesNotContain("S3cret-pass!")
                .doesNotContain("555");
    }

    @Test
    void malformedEventGoesToTheDlqAndTheNextOneIsStored() {
        UUID next = UUID.randomUUID();
        String marker = "no-id-" + UUID.randomUUID();

        kafkaTemplate.send("audit-events", marker, """
                {"source":"UMS","action":"USER_DISABLED","occurredAt":"%s"}""".formatted(Instant.now()));
        kafkaTemplate.send("audit-events", "user-1", umsEvent(next, Instant.now()));

        awaitRecord(next);
        await().atMost(WAIT)
                .until(() -> TestKafka.readAll(kafka, "audit-events-dlq", Duration.ofSeconds(2)).stream()
                        .anyMatch(record -> marker.equals(record.key())));
    }

    @Test
    void roleMappingAdminEventTargetsTheUser() {
        String keycloakId = UUID.randomUUID().toString();
        kafkaTemplate.send(
                "keycloak-events", "u", """
                {"id":"%s","time":%d,"realm":"crm-internal","kind":"ADMIN","operationType":"CREATE",
                 "resourceType":"REALM_ROLE_MAPPING","resourcePath":"users/u-9/role-mappings/realm",
                 "authDetails":{"userId":"svc-1"},"representation":"[{\\"name\\":\\"x\\"}]"}""".formatted(keycloakId, Instant.now().toEpochMilli()));

        AuditRecord record = awaitRecord(UUID.fromString(keycloakId));
        assertThat(record.targetType()).isEqualTo("USER");
        assertThat(record.targetId()).isEqualTo("u-9");
    }

    @Test
    void unreadableRepresentationIsStoredWithoutIt() {
        String keycloakId = UUID.randomUUID().toString();
        kafkaTemplate.send(
                "keycloak-events", "u", """
                {"id":"%s","time":%d,"realm":"portal","kind":"ADMIN","operationType":"UPDATE","resourceType":"USER",
                 "resourcePath":"users/u-4","authDetails":{"userId":"svc-1"},"representation":"password=hunter2"}""".formatted(keycloakId, Instant.now().toEpochMilli()));

        AuditRecord record = awaitRecord(UUID.fromString(keycloakId));
        assertThat(record.after()).doesNotContain("hunter2").contains("unreadable");
    }
}
