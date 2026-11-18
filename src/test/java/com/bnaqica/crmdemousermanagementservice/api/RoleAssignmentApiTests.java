package com.bnaqica.crmdemousermanagementservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.entity.OutboxEvent;
import com.bnaqica.crmdemousermanagementservice.repository.OutboxEventRepository;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import com.bnaqica.crmdemousermanagementservice.support.KeycloakTestAdmin;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** roles spec, "Assignability" and "Assign and revoke roles". */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class RoleAssignmentApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> TICKET = Map.of("ticketRef", "OPS-3");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private OutboxEventRepository outbox;

    private AdminApi api;
    private KeycloakTestAdmin kcAdmin;
    private String sub;

    @BeforeEach
    void setUp() throws Exception {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        kcAdmin = new KeycloakTestAdmin(keycloak);
        sub = api.createInternal("ra-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example");
    }

    private org.springframework.test.web.servlet.ResultActions assign(String user, String role) throws Exception {
        return api.post("/admin/users/" + user + "/roles", Map.of("role", role, "ticketRef", "OPS-3"));
    }

    private String externalUser() throws Exception {
        return AdminApi.field(
                api.post(
                        "/admin/users/external",
                        Map.of(
                                "email", "rx-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.example",
                                "firstName", "Pat",
                                "lastName", "Ext",
                                "role", "external-starter",
                                "ticketRef", "OPS-3")),
                "sub");
    }

    private List<JsonNode> outboxEvents(String topic) {
        return outbox.findAll().stream()
                .filter(event ->
                        event.getTopic().equals(topic) && event.getMessageKey().equals(sub))
                .map(OutboxEvent::getPayload)
                .map(JSON::readTree)
                .toList();
    }

    @Test
    void roleAssigned() throws Exception {
        assign(sub, "team-manager")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(hasItem("team-manager")));

        assertThat(kcAdmin.clientRoleMappings("crm-internal", sub, "crm-api")).contains("team-manager");
        assertThat(outboxEvents("audit-events"))
                .filteredOn(event -> event.get("action").asString().equals("ROLE_ASSIGNED"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.get("ticketRef").asString()).isEqualTo("OPS-3");
                    assertThat(event.get("before").get("roles").toString()).doesNotContain("team-manager");
                    assertThat(event.get("after").get("roles").toString()).contains("team-manager");
                });
        assertThat(outboxEvents("ums-user-events"))
                .filteredOn(event -> event.get("type").asString().equals("RoleAssigned"))
                .singleElement()
                .satisfies(event -> assertThat(event.get("role").asString()).isEqualTo("team-manager"));
    }

    @Test
    void userRolesListed() throws Exception {
        assign(sub, "team-member").andExpect(status().isOk());

        api.get("/admin/users/" + sub + "/roles").andExpect(jsonPath("$.roles").value(hasItem("team-member")));
    }

    @Test
    void roleRevoked() throws Exception {
        assign(sub, "team-manager").andExpect(status().isOk());

        api.delete("/admin/users/" + sub + "/roles/team-manager", TICKET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(not(hasItem("team-manager"))));

        assertThat(kcAdmin.clientRoleMappings("crm-internal", sub, "crm-api")).doesNotContain("team-manager");
        assertThat(outboxEvents("audit-events"))
                .filteredOn(event -> event.get("action").asString().equals("ROLE_REVOKED"))
                .hasSize(1);
        assertThat(outboxEvents("ums-user-events"))
                .filteredOn(event -> event.get("type").asString().equals("RoleRevoked"))
                .hasSize(1);
    }

    @Test
    void internalRoleOnExternalUserIsRejected() throws Exception {
        assign(externalUser(), "team-member").andExpect(status().isUnprocessableContent());
    }

    @Test
    void externalRoleOnInternalUserIsRejected() throws Exception {
        assign(sub, "external-standard").andExpect(status().isUnprocessableContent());
    }

    @Test
    void builtInPortalRoleIsTheRealmRole() throws Exception {
        String external = externalUser();

        assign(external, "external-standard").andExpect(status().isOk());

        assertThat(kcAdmin.realmRoleNames("portal", external)).contains("external-standard");
    }

    @Test
    void unknownRoleIsNotFound() throws Exception {
        assign(sub, "no-such-role").andExpect(status().isNotFound());
    }

    @Test
    void revokingARoleTheUserLacksIsNotFound() throws Exception {
        api.delete("/admin/users/" + sub + "/roles/team-manager", TICKET).andExpect(status().isNotFound());
    }

    @Test
    void assignWithoutTicketIsRejected() throws Exception {
        api.post("/admin/users/" + sub + "/roles", Map.of("role", "team-member"))
                .andExpect(status().isBadRequest());
    }
}
