package com.bnaqica.crmdemousermanagementservice.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import com.bnaqica.crmdemousermanagementservice.support.KeycloakTestAdmin;
import com.bnaqica.crmdemousermanagementservice.support.TestTokens;
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

/** team-scope spec: the lookup, its callers and unknown users. Real tokens from the test Keycloak. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class TeamScopeApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    private AdminApi api;
    private TestTokens tokens;
    private String sub;

    @BeforeEach
    void setUp() throws Exception {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        tokens = new TestTokens(keycloak);
        sub = api.createInternal("sc-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example");
    }

    private ResultActions scope(String user, String token) throws Exception {
        return mockMvc.perform(
                get("/internal/users/" + user + "/team-scope").header("Authorization", "Bearer " + token));
    }

    private String teamWith(String user, boolean manager) throws Exception {
        String team = api.createTeam("Scope " + UUID.randomUUID());
        api.post("/admin/teams/" + team + "/members", Map.of("sub", user, "manager", manager, "ticketRef", "OPS-9"))
                .andExpect(status().isCreated());
        return team;
    }

    @Test
    void teamMember() throws Exception {
        String team = teamWith(sub, false);

        scope(sub, tokens.teamScopeClientToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(sub))
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andExpect(jsonPath("$.memberTeams[0]").value(team))
                .andExpect(jsonPath("$.memberTeams.length()").value(1))
                .andExpect(jsonPath("$.managedTeams").isEmpty())
                .andExpect(jsonPath("$.allTeams").value(false));
    }

    @Test
    void managerSet() throws Exception {
        String team = teamWith(sub, true);

        scope(sub, tokens.teamScopeClientToken())
                .andExpect(jsonPath("$.memberTeams[0]").value(team))
                .andExpect(jsonPath("$.managedTeams[0]").value(team));
    }

    @Test
    void superManager() throws Exception {
        String team = teamWith(sub, false);
        new KeycloakTestAdmin(keycloak).grantClientRole("crm-internal", sub, "crm-api", "super-manager");

        scope(sub, tokens.teamScopeClientToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allTeams").value(true))
                .andExpect(jsonPath("$.memberTeams.length()").value(1))
                .andExpect(jsonPath("$.memberTeams[0]").value(team));
    }

    @Test
    void externalUserSubIsNotFound() throws Exception {
        String external = AdminApi.field(
                api.post(
                        "/admin/users/external",
                        Map.of(
                                "email", "sx-" + UUID.randomUUID().toString().substring(0, 8) + "@acme.example",
                                "firstName", "Pat",
                                "lastName", "Ext",
                                "role", "external-standard",
                                "ticketRef", "OPS-9")),
                "sub");

        scope(external, tokens.teamScopeClientToken()).andExpect(status().isNotFound());
    }

    @Test
    void unknownSubIsNotFound() throws Exception {
        scope(UUID.randomUUID().toString(), tokens.teamScopeClientToken()).andExpect(status().isNotFound());
    }

    @Test
    void userTokenIsForbidden() throws Exception {
        scope(sub, tokens.userToken("crm-internal", "test-employee")).andExpect(status().isForbidden());
    }

    @Test
    void adminTokenIsForbidden() throws Exception {
        scope(sub, tokens.adminToken()).andExpect(status().isForbidden());
    }

    @Test
    void serviceTokenWithoutTheScopeIsForbidden() throws Exception {
        String promoter = tokens.clientToken("crm-internal", "crm-promoter", "crm-promoter-local-secret");

        scope(sub, promoter).andExpect(status().isForbidden());
    }

    @Test
    void portalTokenIsForbidden() throws Exception {
        scope(sub, tokens.userToken("portal", "test-portal@acme.example")).andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/internal/users/" + sub + "/team-scope")).andExpect(status().isUnauthorized());
    }
}
