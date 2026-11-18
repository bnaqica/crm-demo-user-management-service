package com.bnaqica.crmdemousermanagementservice.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.entity.OutboxEvent;
import com.bnaqica.crmdemousermanagementservice.repository.OutboxEventRepository;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
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

/** teams spec, "Membership changes need a ticket" and "Membership events". */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class TeamEventsTests {

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
    private OutboxEventRepository outbox;

    @Autowired
    private UserEventPublisher userEvents;

    @Autowired
    private TransactionTemplate transactions;

    private AdminApi api;
    private String team;
    private String sub;

    @BeforeEach
    void setUp() throws Exception {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        team = api.createTeam("Events " + UUID.randomUUID());
        sub = api.createInternal("ev-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example");
    }

    private void addMember() throws Exception {
        api.post("/admin/teams/" + team + "/members", Map.of("sub", sub, "ticketRef", "OPS-9"))
                .andExpect(status().isCreated());
    }

    /** What the change wrote to the outbox for this key and topic; the relay publishes exactly these. */
    private List<JsonNode> outboxEvents(String topic, String key) {
        return outbox.findAll().stream()
                .filter(event ->
                        event.getTopic().equals(topic) && event.getMessageKey().equals(key))
                .map(OutboxEvent::getPayload)
                .map(JSON::readTree)
                .toList();
    }

    private List<JsonNode> audits(String key, String action) {
        return outboxEvents("audit-events", key).stream()
                .filter(event -> event.get("action").asString().equals(action))
                .toList();
    }

    private List<JsonNode> membershipEvents() {
        return outboxEvents("ums-user-events", sub).stream()
                .filter(event -> event.get("type").asString().equals("TeamMembershipChanged"))
                .sorted(java.util.Comparator.comparing(
                        event -> Instant.parse(event.get("occurredAt").asString())))
                .toList();
    }

    @Test
    void memberRemovalAudited() throws Exception {
        addMember();

        api.delete("/admin/teams/" + team + "/members/" + sub, Map.of("ticketRef", "OPS-9"))
                .andExpect(status().isNoContent());

        JsonNode audit = audits(sub, "TEAM_MEMBER_REMOVED").getFirst();
        assertThat(audit.get("outcome").asString()).isEqualTo("SUCCESS");
        assertThat(audit.get("ticketRef").asString()).isEqualTo("OPS-9");
        assertThat(audit.get("targetType").asString()).isEqualTo("USER");
        assertThat(audit.get("realm").asString()).isEqualTo("crm-internal");
        assertThat(audit.get("before").get("teamId").asString()).isEqualTo(team);
        assertThat(audit.get("before").get("manager").asBoolean()).isFalse();
        assertThat(audit.get("after").isNull()).isTrue();
    }

    @Test
    void managerChangeAuditedWithBeforeAndAfter() throws Exception {
        addMember();

        api.put("/admin/teams/" + team + "/members/" + sub, Map.of("manager", true, "ticketRef", "OPS-9"))
                .andExpect(status().isOk());

        JsonNode audit = audits(sub, "TEAM_MANAGER_SET").getFirst();
        assertThat(audit.get("before").get("manager").asBoolean()).isFalse();
        assertThat(audit.get("after").get("manager").asBoolean()).isTrue();
    }

    @Test
    void teamCreationAuditedAgainstTheTeam() throws Exception {
        JsonNode audit = audits(team, "TEAM_CREATED").getFirst();

        assertThat(audit.get("targetType").asString()).isEqualTo("TEAM");
        assertThat(audit.get("ticketRef").asString()).isEqualTo("OPS-1");
        assertThat(audit.get("after").get("name").asString()).startsWith("Events ");
    }

    @Test
    void multiTeamChangeAudited() throws Exception {
        api.put("/admin/users/" + sub + "/multi-team", Map.of("allowed", true, "ticketRef", "OPS-9"))
                .andExpect(status().isOk());

        JsonNode audit = audits(sub, "USER_MULTI_TEAM_CHANGED").getFirst();
        assertThat(audit.get("before").get("multiTeamAllowed").asBoolean()).isFalse();
        assertThat(audit.get("after").get("multiTeamAllowed").asBoolean()).isTrue();
    }

    @Test
    void eventPublishedAfterMemberAdded() throws Exception {
        addMember();

        assertThat(membershipEvents()).singleElement().satisfies(event -> {
            assertThat(event.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(event.get("eventId").asString()).isNotBlank();
            assertThat(event.get("sub").asString()).isEqualTo(sub);
            assertThat(event.get("teamId").asString()).isEqualTo(team);
            assertThat(event.get("member").asBoolean()).isTrue();
            assertThat(event.get("manager").asBoolean()).isFalse();
        });
        await().atMost(Duration.ofSeconds(30))
                .until(() -> TestKafka.readAll(kafka, "ums-user-events", Duration.ofSeconds(2)).stream()
                        .filter(record -> sub.equals(record.key()))
                        .map(ConsumerRecord::value)
                        .anyMatch(value -> value.contains("TeamMembershipChanged")));
    }

    @Test
    void rejectedChangePublishesNoEventButAuditsTheFailure() throws Exception {
        api.post("/admin/teams/" + team + "/archive", Map.of("ticketRef", "OPS-9"))
                .andExpect(status().isOk());

        api.post("/admin/teams/" + team + "/members", Map.of("sub", sub, "ticketRef", "OPS-9"))
                .andExpect(status().isConflict());

        assertThat(membershipEvents()).isEmpty();
        assertThat(audits(sub, "TEAM_MEMBER_ADDED")).singleElement().satisfies(audit -> {
            assertThat(audit.get("outcome").asString()).isEqualTo("FAILURE");
            assertThat(audit.get("realm").asString()).isEqualTo("crm-internal");
        });
    }

    @Test
    void nothingPublishedWhenTheChangeRollsBack() {
        try {
            transactions.executeWithoutResult(status -> {
                userEvents.teamMembershipChanged(UUID.fromString(sub), UUID.fromString(team), true, false);
                throw new IllegalStateException("simulated failure after the event was written");
            });
        } catch (IllegalStateException expected) {
            // rolled back
        }

        assertThat(membershipEvents()).isEmpty();
    }

    @Test
    void eventPublishedAfterMemberRemoved() throws Exception {
        addMember();

        api.delete("/admin/teams/" + team + "/members/" + sub, Map.of("ticketRef", "OPS-9"))
                .andExpect(status().isNoContent());

        assertThat(membershipEvents()).hasSize(2).last().satisfies(event -> {
            assertThat(event.get("member").asBoolean()).isFalse();
            assertThat(event.get("teamId").asString()).isEqualTo(team);
        });
    }

    @Test
    void eventPublishedAfterManagerChange() throws Exception {
        addMember();

        api.put("/admin/teams/" + team + "/members/" + sub, Map.of("manager", true, "ticketRef", "OPS-9"))
                .andExpect(status().isOk());

        assertThat(membershipEvents()).hasSize(2).last().satisfies(event -> {
            assertThat(event.get("member").asBoolean()).isTrue();
            assertThat(event.get("manager").asBoolean()).isTrue();
        });
    }

    @Test
    void managerSetToItsCurrentValueChangesNothing() throws Exception {
        addMember();

        api.put("/admin/teams/" + team + "/members/" + sub, Map.of("manager", false, "ticketRef", "OPS-9"))
                .andExpect(status().isOk());

        assertThat(membershipEvents()).hasSize(1);
        assertThat(audits(sub, "TEAM_MANAGER_UNSET")).isEmpty();
    }

    @Test
    void teamArchiveAudited() throws Exception {
        api.post("/admin/teams/" + team + "/archive", Map.of("ticketRef", "OPS-9"))
                .andExpect(status().isOk());

        JsonNode audit = audits(team, "TEAM_ARCHIVED").getFirst();
        assertThat(audit.get("targetType").asString()).isEqualTo("TEAM");
        assertThat(audit.get("ticketRef").asString()).isEqualTo("OPS-9");
        assertThat(audit.get("before").get("archivedAt").isNull()).isTrue();
        assertThat(audit.get("after").get("archivedAt").isNull()).isFalse();
    }

    @Test
    void failedArchiveAuditedWithTheRealm() throws Exception {
        api.post("/admin/teams/" + team + "/archive", Map.of("ticketRef", "OPS-9"))
                .andExpect(status().isOk());
        api.post("/admin/teams/" + team + "/archive", Map.of("ticketRef", "OPS-9"))
                .andExpect(status().isConflict());

        assertThat(audits(team, "TEAM_ARCHIVED"))
                .filteredOn(audit -> audit.get("outcome").asString().equals("FAILURE"))
                .singleElement()
                .satisfies(audit -> assertThat(audit.get("realm").asString()).isEqualTo("crm-internal"));
    }
}
