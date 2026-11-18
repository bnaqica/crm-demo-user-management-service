package com.bnaqica.crmdemousermanagementservice.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.repository.OutboxEventRepository;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.KeycloakTestAdmin;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

/** starter-expiry spec, "Self-registered users recorded as starters": portal REGISTER events on keycloak-events. */
@SpringBootTest
@Import(IntegrationTestContainers.class)
class StarterRegistrationTests {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private OutboxEventRepository outbox;

    private void register(String eventId, String userId) {
        kafkaTemplate.send(
                "keycloak-events", userId, """
                {"id":"%s","time":%d,"realm":"portal","type":"REGISTER","userId":"%s","details":{}}""".formatted(eventId, Instant.now().toEpochMilli(), userId));
    }

    private long userCreatedEvents(String sub) {
        return outbox.findAll().stream()
                .filter(event -> event.getTopic().equals("ums-user-events")
                        && event.getMessageKey().equals(sub))
                .filter(event -> event.getPayload().contains("\"UserCreated\""))
                .count();
    }

    @Test
    void registrationRecordsAStarter() {
        KeycloakTestAdmin kcAdmin = new KeycloakTestAdmin(keycloak);
        String email = "self-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.example";
        String sub = kcAdmin.createUser("portal", email);

        register(UUID.randomUUID().toString(), sub);

        await().atMost(WAIT).until(() -> accounts.find(UUID.fromString(sub)).isPresent());
        UserAccount account = accounts.get(UUID.fromString(sub));
        assertThat(account.getUserType()).isEqualTo(UserType.EXTERNAL);
        assertThat(account.getStatus()).isEqualTo(UserStatus.STARTER);
        assertThat(account.getEmail()).isEqualTo(email);
        assertThat(kcAdmin.realmRoleNames("portal", sub)).contains("external-starter");
        assertThat(userCreatedEvents(sub)).isEqualTo(1);
    }

    @Test
    void duplicateRegistrationEventIsRecordedOnce() throws Exception {
        KeycloakTestAdmin kcAdmin = new KeycloakTestAdmin(keycloak);
        String sub = kcAdmin.createUser(
                "portal", "dup-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.example");
        String eventId = UUID.randomUUID().toString();

        register(eventId, sub);
        register(eventId, sub);

        await().atMost(WAIT).until(() -> accounts.find(UUID.fromString(sub)).isPresent());
        Thread.sleep(3000); // let the second delivery be handled too
        assertThat(userCreatedEvents(sub)).isEqualTo(1);
        assertThat(accounts.get(UUID.fromString(sub)).getVersion()).isZero();
    }
}
