package com.bnaqica.crmdemousermanagementservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.entity.GrantRequest;
import com.bnaqica.crmdemousermanagementservice.entity.OutboxEvent;
import com.bnaqica.crmdemousermanagementservice.repository.GrantRequestRepository;
import com.bnaqica.crmdemousermanagementservice.repository.OutboxEventRepository;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import com.bnaqica.crmdemousermanagementservice.support.KeycloakTestAdmin;
import java.time.Instant;
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

/** privileged-grant-approval spec: Super Manager and new admins need a second admin. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class GrantRequestApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> TICKET = Map.of("ticketRef", "OPS-21");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private GrantRequestRepository grantRequests;

    @Autowired
    private OutboxEventRepository outbox;

    private AdminApi adminA;
    private AdminApi adminB;
    private String sub;

    @BeforeEach
    void setUp() throws Exception {
        adminA = new AdminApi(mockMvc, keycloak, mailpit);
        adminB = new AdminApi(mockMvc, keycloak, mailpit, "test-admin-2");
        sub = adminA.createInternal("gr-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example");
    }

    private String requestSuperManager() throws Exception {
        return AdminApi.field(
                adminA.post("/admin/users/" + sub + "/roles", Map.of("role", "super-manager", "ticketRef", "OPS-21"))
                        .andExpect(status().isAccepted()),
                "id");
    }

    @Test
    void superManagerRequested() throws Exception {
        adminA.post("/admin/users/" + sub + "/roles", Map.of("role", "super-manager", "ticketRef", "OPS-21"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("SUPER_MANAGER"))
                .andExpect(jsonPath("$.target").value(sub))
                .andExpect(jsonPath("$.requestedBy").value(adminA.adminSub()));

        adminA.get("/admin/users/" + sub + "/roles")
                .andExpect(jsonPath("$.roles").value(not(hasItem("super-manager"))));
    }

    @Test
    void requesterApprovingOwnRequestIsForbidden() throws Exception {
        String id = requestSuperManager();

        adminA.post("/admin/grant-requests/" + id + "/approve", TICKET).andExpect(status().isForbidden());

        adminA.get("/admin/grant-requests/" + id).andExpect(jsonPath("$.status").value("PENDING"));
        adminA.get("/admin/users/" + sub + "/roles")
                .andExpect(jsonPath("$.roles").value(not(hasItem("super-manager"))));
    }

    @Test
    void anotherAdminApproves() throws Exception {
        String id = requestSuperManager();

        adminB.post("/admin/grant-requests/" + id + "/approve", TICKET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value(adminB.adminSub()));

        adminA.get("/admin/users/" + sub + "/roles")
                .andExpect(jsonPath("$.roles").value(hasItem("super-manager")));
        JsonNode audit = outbox.findAll().stream()
                .filter(event -> event.getTopic().equals("audit-events")
                        && event.getMessageKey().equals(id))
                .map(OutboxEvent::getPayload)
                .map(JSON::readTree)
                .filter(event -> event.get("action").asString().equals("GRANT_REQUEST_APPROVED"))
                .findFirst()
                .orElseThrow();
        assertThat(audit.get("actorId").asString()).isEqualTo(adminB.adminSub());
        assertThat(audit.get("after").get("requestedBy").asString()).isEqualTo(adminA.adminSub());
        assertThat(audit.get("after").get("decidedBy").asString()).isEqualTo(adminB.adminSub());
    }

    @Test
    void rejectedRequestHasNoEffect() throws Exception {
        String id = requestSuperManager();

        adminA.post("/admin/grant-requests/" + id + "/reject", TICKET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        adminB.post("/admin/grant-requests/" + id + "/approve", TICKET).andExpect(status().isConflict());
        adminA.get("/admin/users/" + sub + "/roles")
                .andExpect(jsonPath("$.roles").value(not(hasItem("super-manager"))));
    }

    @Test
    void pendingListHoldsOnlyPendingUnexpiredRequests() throws Exception {
        String pending = requestSuperManager();
        String rejected = requestSuperManager();
        adminA.post("/admin/grant-requests/" + rejected + "/reject", TICKET).andExpect(status().isOk());
        String expired = requestSuperManager();
        expire(expired);

        adminA.get("/admin/grant-requests?status=PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(pending)))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(rejected))))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(expired))));
    }

    @Test
    void expiredRequestCannotBeApproved() throws Exception {
        String id = requestSuperManager();
        expire(id);

        adminB.post("/admin/grant-requests/" + id + "/approve", TICKET).andExpect(status().isConflict());

        adminA.get("/admin/grant-requests/" + id).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void adminAccountCreatedOnlyAfterApproval() throws Exception {
        String email = "new-admin-" + UUID.randomUUID().toString().substring(0, 8) + "@crm.local";
        KeycloakTestAdmin kcAdmin = new KeycloakTestAdmin(keycloak);

        String id = AdminApi.field(
                adminA.post(
                                "/admin/admins",
                                Map.of("email", email, "firstName", "New", "lastName", "Admin", "ticketRef", "OPS-22"))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.type").value("ADMIN_ACCOUNT")),
                "id");
        assertThat(kcAdmin.findByEmail("crm-admin", email)).isNull();

        adminB.post("/admin/grant-requests/" + id + "/approve", TICKET).andExpect(status().isOk());

        Map<String, Object> created = kcAdmin.findByEmail("crm-admin", email);
        assertThat(created).isNotNull();
        assertThat(kcAdmin.realmRoleNames("crm-admin", (String) created.get("id")))
                .contains("admin");
    }

    private void expire(String id) {
        GrantRequest request = grantRequests.findById(UUID.fromString(id)).orElseThrow();
        request.setExpiresAt(Instant.now().minusSeconds(60));
        grantRequests.saveAndFlush(request);
    }
}
