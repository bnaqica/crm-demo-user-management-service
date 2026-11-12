package com.bnaqica.crmdemousermanagementservice.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import com.bnaqica.crmdemousermanagementservice.support.KeycloakTestAdmin;
import com.bnaqica.crmdemousermanagementservice.support.TestKafka;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** user-accounts spec, "User events": published to ums-user-events through the outbox, only after commit. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class UserEventsTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TOPIC = "ums-user-events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private UserEventPublisher userEvents;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private TransactionTemplate transactions;

    private AdminApi api;
    private String email;

    @BeforeEach
    void setUp() {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        email = "e-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example";
    }

    private List<JsonNode> eventsFor(String sub, Duration wait) {
        return TestKafka.readAll(kafka, TOPIC, wait).stream()
                .filter(record -> sub.equals(record.key()))
                .map(ConsumerRecord::value)
                .map(JSON::readTree)
                .toList();
    }

    @Test
    void userCreatedPublished() throws Exception {
        String sub = api.createInternal(email);

        await().atMost(Duration.ofSeconds(30))
                .until(() -> !eventsFor(sub, Duration.ofSeconds(2)).isEmpty());
        JsonNode event = eventsFor(sub, Duration.ofSeconds(2)).getFirst();
        assertThat(event.get("type").asString()).isEqualTo("UserCreated");
        assertThat(event.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(event.get("eventId").asString()).isNotBlank();
        assertThat(event.get("sub").asString()).isEqualTo(sub);
        assertThat(event.get("userType").asString()).isEqualTo("INTERNAL");
        assertThat(event.get("email").asString()).isEqualTo(email);
    }

    @Test
    void userDisabledPublished() throws Exception {
        String sub = api.createInternal(email);
        new KeycloakTestAdmin(keycloak).makeLoginReady("crm-internal", sub);
        UserAccount account = accounts.get(UUID.fromString(sub));
        account.setStatus(UserStatus.ACTIVE);
        accounts.update(account);

        api.post("/admin/users/" + sub + "/disable", Map.of("ticketRef", "OPS-40"));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> eventsFor(sub, Duration.ofSeconds(2)).stream()
                        .anyMatch(event -> event.get("type").asString().equals("UserDisabled")));
    }

    @Test
    void nothingPublishedWhenTheChangeRollsBack() {
        UUID sub = UUID.randomUUID();
        UserAccount account = UserAccount.builder()
                .sub(sub)
                .userType(UserType.INTERNAL)
                .email(email)
                .status(UserStatus.INVITED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            transactions.executeWithoutResult(status -> {
                accounts.create(account);
                userEvents.userCreated(account);
                throw new IllegalStateException("simulated failure after the event was written");
            });
        } catch (IllegalStateException expected) {
            // rolled back
        }

        assertThat(eventsFor(sub.toString(), Duration.ofSeconds(8))).isEmpty();
        assertThat(accounts.find(sub)).isEmpty();
    }
}
