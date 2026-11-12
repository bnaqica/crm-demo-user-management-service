package com.bnaqica.crmdemousermanagementservice.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.TestKafka;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Events on {@code keycloak-events}, in the format the Keycloak event-listener SPI publishes:
 * {@code {id, time, realm, type, userId, error, details}}.
 */
@SpringBootTest
@Import(IntegrationTestContainers.class)
class KeycloakEventListenerTests {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private MeterRegistry meters;

    private UUID invitedUser() {
        UUID sub = UUID.randomUUID();
        accounts.create(UserAccount.builder()
                .sub(sub)
                .userType(UserType.INTERNAL)
                .email("ev-" + sub + "@corp.example")
                .status(UserStatus.INVITED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        return sub;
    }

    private void send(String eventId, String type, Object userId, Instant time) {
        kafkaTemplate.send(
                "keycloak-events", String.valueOf(userId), """
                {"id":"%s","time":%d,"realm":"crm-internal","type":"%s","userId":"%s","details":{}}""".formatted(eventId, time.toEpochMilli(), type, userId));
    }

    private double alerts() {
        var counter = meters.find("ums.alerts").tag("type", "mass-lockout").counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void invitedUserBecomesActiveOnFirstLogin() {
        UUID sub = invitedUser();

        send(UUID.randomUUID().toString(), "LOGIN", sub, Instant.now());

        await().atMost(WAIT).until(() -> accounts.get(sub).getStatus() == UserStatus.ACTIVE);
    }

    @Test
    void duplicateLoginEventChangesNothingMore() {
        UUID sub = invitedUser();
        String eventId = UUID.randomUUID().toString();

        send(eventId, "LOGIN", sub, Instant.now());
        send(eventId, "LOGIN", sub, Instant.now());

        await().atMost(WAIT).until(() -> accounts.get(sub).getStatus() == UserStatus.ACTIVE);
        assertThat(accounts.get(sub).getVersion()).isEqualTo(1);
    }

    @Test
    void severalAccountsLockingAtOnceRaiseAnAlert() {
        double before = alerts();
        Instant start = Instant.parse("2030-01-01T10:00:00Z");

        for (int i = 0; i < 10; i++) {
            send(
                    UUID.randomUUID().toString(),
                    "USER_DISABLED_BY_TEMPORARY_LOCKOUT",
                    UUID.randomUUID(),
                    start.plusSeconds(i * 20));
        }

        await().atMost(WAIT).until(() -> alerts() == before + 1);
    }

    @Test
    void duplicateLockoutEventsAreCountedOnce() {
        double before = alerts();
        Instant start = Instant.parse("2031-01-01T10:00:00Z");
        UUID marker = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            String eventId = UUID.randomUUID().toString();
            UUID user = UUID.randomUUID();
            send(eventId, "USER_DISABLED_BY_TEMPORARY_LOCKOUT", user, start.plusSeconds(i));
            send(eventId, "USER_DISABLED_BY_TEMPORARY_LOCKOUT", user, start.plusSeconds(i));
        }
        // Processed after the lockouts on the same partition order isn't guaranteed, so wait for a marker login
        UUID sub = invitedUser();
        send(marker.toString(), "LOGIN", sub, start);
        await().atMost(WAIT).until(() -> accounts.get(sub).getStatus() == UserStatus.ACTIVE);

        await().during(Duration.ofSeconds(3)).atMost(WAIT).until(() -> alerts() == before);
    }

    @Test
    void malformedEventGoesToTheDeadLetterTopic() {
        String marker = UUID.randomUUID().toString();

        kafkaTemplate.send("keycloak-events", marker, "{not json " + marker);

        await().atMost(Duration.ofSeconds(60))
                .until(() -> TestKafka.readAll(kafka, "keycloak-events-dlq", Duration.ofSeconds(2)).stream()
                        .anyMatch(record -> marker.equals(record.key())));
    }
}
