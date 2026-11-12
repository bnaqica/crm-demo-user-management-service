package com.bnaqica.crmdemousermanagementservice.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import com.bnaqica.crmdemousermanagementservice.support.TestKafka;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** user-accounts spec, "Admin actions audited": every admin write, success or failure, goes to audit-events. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class AdminAuditTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private UserAccountService accounts;

    private AdminApi api;
    private String email;

    @BeforeEach
    void setUp() {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        email = "a-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example";
    }

    private JsonNode auditEvent(String correlationId) {
        await().atMost(Duration.ofSeconds(30)).until(() -> find(correlationId).isPresent());
        return find(correlationId).orElseThrow();
    }

    private Optional<JsonNode> find(String correlationId) {
        List<JsonNode> events = TestKafka.readAll(kafka, "audit-events", Duration.ofSeconds(2)).stream()
                .map(ConsumerRecord::value)
                .map(JSON::readTree)
                .filter(event ->
                        correlationId.equals(event.path("correlationId").asString()))
                .toList();
        return events.stream().findFirst();
    }

    private String activeUser() throws Exception {
        String sub = api.createInternal(email);
        UserAccount account = accounts.get(UUID.fromString(sub));
        account.setStatus(UserStatus.ACTIVE);
        accounts.update(account);
        return sub;
    }

    @Test
    void auditOfADisable() throws Exception {
        String sub = activeUser();
        String correlationId = UUID.randomUUID().toString();

        api.post("/admin/users/" + sub + "/disable", Map.of("ticketRef", "OPS-40"), correlationId)
                .andExpect(status().isOk());

        JsonNode event = auditEvent(correlationId);
        assertThat(event.get("action").asString()).isEqualTo("USER_DISABLED");
        assertThat(event.get("outcome").asString()).isEqualTo("SUCCESS");
        assertThat(event.get("source").asString()).isEqualTo("UMS");
        assertThat(event.get("realm").asString()).isEqualTo("crm-internal");
        assertThat(event.get("actorId").asString()).isEqualTo(api.adminSub());
        assertThat(event.get("actorType").asString()).isEqualTo("ADMIN");
        assertThat(event.get("targetType").asString()).isEqualTo("USER");
        assertThat(event.get("targetId").asString()).isEqualTo(sub);
        assertThat(event.get("ticketRef").asString()).isEqualTo("OPS-40");
        assertThat(event.get("before").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(event.get("after").get("status").asString()).isEqualTo("DISABLED");
        assertThat(event.get("eventId").asString()).isNotBlank();
        assertThat(event.get("schemaVersion").asInt()).isEqualTo(1);
    }

    @Test
    void failedActionIsAudited() throws Exception {
        String sub = api.createInternal(email); // INVITED, so it can't be disabled
        String correlationId = UUID.randomUUID().toString();

        api.post("/admin/users/" + sub + "/disable", Map.of("ticketRef", "OPS-41"), correlationId)
                .andExpect(status().isConflict());

        JsonNode event = auditEvent(correlationId);
        assertThat(event.get("action").asString()).isEqualTo("USER_DISABLED");
        assertThat(event.get("outcome").asString()).isEqualTo("FAILURE");
        assertThat(event.get("ticketRef").asString()).isEqualTo("OPS-41");
        assertThat(event.get("targetId").asString()).isEqualTo(sub);
        assertThat(event.get("reason").asString()).contains("INVITED");
    }

    @Test
    void creationIsAudited() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        api.post(
                        "/admin/users/internal",
                        Map.of("email", email, "firstName", "Ana", "lastName", "Test", "ticketRef", "OPS-42"),
                        correlationId)
                .andExpect(status().isCreated());

        JsonNode event = auditEvent(correlationId);
        assertThat(event.get("action").asString()).isEqualTo("USER_CREATED");
        assertThat(event.get("before").isNull()).isTrue();
        assertThat(event.get("after").get("email").asString()).isEqualTo(email);
        assertThat(event.get("after").get("status").asString()).isEqualTo("INVITED");
    }

    @Test
    void unlockIsAudited() throws Exception {
        String sub = activeUser();
        String correlationId = UUID.randomUUID().toString();

        api.post("/admin/users/" + sub + "/unlock", Map.of("ticketRef", "OPS-43"), correlationId)
                .andExpect(status().isOk());

        JsonNode event = auditEvent(correlationId);
        assertThat(event.get("action").asString()).isEqualTo("USER_UNLOCKED");
        assertThat(event.get("outcome").asString()).isEqualTo("SUCCESS");
        assertThat(event.get("ticketRef").asString()).isEqualTo("OPS-43");
    }
}
