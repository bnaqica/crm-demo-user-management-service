package com.bnaqica.crmdemousermanagementservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.entity.OutboxEvent;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.repository.OutboxEventRepository;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import com.bnaqica.crmdemousermanagementservice.support.KeycloakTestAdmin;
import com.bnaqica.crmdemousermanagementservice.support.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** user-promotion spec: promote a portal starter to standard, only as crm-promoter. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class PromotionApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private OutboxEventRepository outbox;

    private AdminApi api;
    private KeycloakTestAdmin kcAdmin;
    private TestTokens tokens;
    private String email;

    @BeforeEach
    void setUp() {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        kcAdmin = new KeycloakTestAdmin(keycloak);
        tokens = new TestTokens(keycloak);
        email = "st-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.example";
    }

    /** A portal user as self-registration leaves them: STARTER, with external-starter. */
    private String starter() throws Exception {
        String sub = AdminApi.field(
                api.post(
                        "/admin/users/external",
                        Map.of(
                                "email", email,
                                "firstName", "Sam",
                                "lastName", "Starter",
                                "role", "external-starter",
                                "ticketRef", "OPS-50")),
                "sub");
        setStatus(sub, UserStatus.STARTER);
        return sub;
    }

    private void setStatus(String sub, UserStatus status) {
        UserAccount account = accounts.get(UUID.fromString(sub));
        account.setStatus(status);
        accounts.update(account);
    }

    private String promoterToken() {
        return tokens.clientToken("crm-internal", "crm-promoter", "crm-promoter-local-secret");
    }

    private ResultActions promote(String sub, String token) throws Exception {
        return mockMvc.perform(post("/internal/users/" + sub + "/promote")
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", "corr-" + sub));
    }

    private List<JsonNode> events(String topic, String sub) {
        return outbox.findAll().stream()
                .filter(event ->
                        event.getTopic().equals(topic) && event.getMessageKey().equals(sub))
                .map(OutboxEvent::getPayload)
                .map(JSON::readTree)
                .toList();
    }

    private List<JsonNode> promotedEvents(String sub) {
        return events("ums-user-events", sub).stream()
                .filter(event -> event.get("type").asString().equals("UserPromoted"))
                .toList();
    }

    @Test
    void starterPromoted() throws Exception {
        String sub = starter();

        promote(sub, promoterToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("external-standard"));

        assertThat(kcAdmin.realmRoleNames("portal", sub))
                .contains("external-standard")
                .doesNotContain("external-starter");
        assertThat(accounts.get(UUID.fromString(sub)).getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(promotedEvents(sub)).singleElement().satisfies(event -> {
            assertThat(event.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(event.get("sub").asString()).isEqualTo(sub);
        });
    }

    @Test
    void promotionAuditedAsTheCallingSystem() throws Exception {
        String sub = starter();

        promote(sub, promoterToken()).andExpect(status().isOk());

        assertThat(events("audit-events", sub))
                .filteredOn(event -> event.get("action").asString().equals("USER_PROMOTED"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.get("actorType").asString()).isEqualTo("SYSTEM");
                    assertThat(event.get("actorId").asString()).isEqualTo("crm-promoter");
                    assertThat(event.get("correlationId").asString()).isEqualTo("corr-" + sub);
                    assertThat(event.get("outcome").asString()).isEqualTo("SUCCESS");
                });
    }

    @Test
    void refusedPromotionAuditedAsAFailure() throws Exception {
        String sub = api.createInternal("pi-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example");

        promote(sub, promoterToken()).andExpect(status().isConflict());

        assertThat(events("audit-events", sub))
                .filteredOn(event -> event.get("action").asString().equals("USER_PROMOTED"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.get("outcome").asString()).isEqualTo("FAILURE");
                    assertThat(event.get("actorId").asString()).isEqualTo("crm-promoter");
                });
    }

    @Test
    void promotingTwiceChangesNothing() throws Exception {
        String sub = starter();
        promote(sub, promoterToken()).andExpect(status().isOk());

        promote(sub, promoterToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(promotedEvents(sub)).hasSize(1);
        assertThat(events("audit-events", sub))
                .filteredOn(event -> event.get("action").asString().equals("USER_PROMOTED"))
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.get("outcome").asString()).isEqualTo("SUCCESS"));
    }

    @Test
    void starterWhoseRolesWereSwappedAlreadyIsActivated() throws Exception {
        String sub = starter();
        kcAdmin.grantRealmRole("portal", sub, "external-standard");
        kcAdmin.removeRealmRole("portal", sub, "external-starter"); // Keycloak done, the UMS step failed

        promote(sub, promoterToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(accounts.get(UUID.fromString(sub)).getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(promotedEvents(sub)).hasSize(1);
    }

    @Test
    void interruptedPromotionIsCompleted() throws Exception {
        String sub = starter();
        kcAdmin.grantRealmRole("portal", sub, "external-standard"); // as if a first attempt stopped half way

        promote(sub, promoterToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(kcAdmin.realmRoleNames("portal", sub)).doesNotContain("external-starter");
        assertThat(promotedEvents(sub)).hasSize(1);
    }

    @Test
    void userPromotingThemselvesIsForbidden() throws Exception {
        String sub = starter();
        kcAdmin.makeLoginReady("portal", sub);
        String own = tokens.userToken("portal", email);

        promote(sub, own).andExpect(status().isForbidden());

        assertThat(kcAdmin.realmRoleNames("portal", sub)).contains("external-starter");
        assertThat(accounts.get(UUID.fromString(sub)).getStatus()).isEqualTo(UserStatus.STARTER);
    }

    @Test
    void otherServiceIsForbidden() throws Exception {
        promote(starter(), tokens.teamScopeClientToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminTokenIsForbidden() throws Exception {
        promote(starter(), tokens.adminToken()).andExpect(status().isForbidden());
    }

    @Test
    void internalUserIsAConflict() throws Exception {
        String sub = api.createInternal("pi-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example");

        promote(sub, promoterToken()).andExpect(status().isConflict());
    }

    @Test
    void disabledUserIsAConflict() throws Exception {
        String sub = starter();
        setStatus(sub, UserStatus.DISABLED);

        promote(sub, promoterToken()).andExpect(status().isConflict());
    }

    @Test
    void userWithNeitherPortalRoleIsAConflict() throws Exception {
        String sub = starter();
        kcAdmin.removeRealmRole("portal", sub, "external-starter");

        promote(sub, promoterToken()).andExpect(status().isConflict());
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        promote(UUID.randomUUID().toString(), promoterToken()).andExpect(status().isNotFound());
    }
}
