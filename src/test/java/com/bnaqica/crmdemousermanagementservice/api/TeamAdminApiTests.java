package com.bnaqica.crmdemousermanagementservice.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** teams spec, "Create and archive teams". */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class TeamAdminApiTests {

    private static final Map<String, String> TICKET = Map.of("ticketRef", "OPS-7");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    private AdminApi api;
    private String name;

    @BeforeEach
    void setUp() {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        name = "Team " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String createTeam(String teamName) throws Exception {
        return AdminApi.field(
                api.post("/admin/teams", Map.of("name", teamName, "ticketRef", "OPS-7"))
                        .andExpect(status().isCreated()),
                "id");
    }

    @Test
    void teamCreated() throws Exception {
        api.post("/admin/teams", Map.of("name", name, "ticketRef", "OPS-7"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void duplicateTeamNameIsAConflict() throws Exception {
        createTeam(name);

        api.post("/admin/teams", Map.of("name", name.toUpperCase(), "ticketRef", "OPS-7"))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithoutTicketIsRejected() throws Exception {
        api.post("/admin/teams", Map.of("name", name)).andExpect(status().isBadRequest());
    }

    @Test
    void teamArchived() throws Exception {
        String id = createTeam(name);

        api.post("/admin/teams/" + id + "/archive", TICKET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
        api.get("/admin/teams/" + id).andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void archivingTwiceIsAConflict() throws Exception {
        String id = createTeam(name);
        api.post("/admin/teams/" + id + "/archive", TICKET).andExpect(status().isOk());

        api.post("/admin/teams/" + id + "/archive", TICKET).andExpect(status().isConflict());
    }

    @Test
    void unknownTeamIsNotFound() throws Exception {
        api.get("/admin/teams/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void teamsListed() throws Exception {
        String id = createTeam(name);

        api.get("/admin/teams")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].name").value(name));
    }

    @Test
    void teamsListedWithMemberAndManagerCounts() throws Exception {
        String id = createTeam(name);
        String manager =
                api.createInternal("cnt-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example");
        api.post("/admin/teams/" + id + "/members", Map.of("sub", manager, "manager", true, "ticketRef", "OPS-7"))
                .andExpect(status().isCreated());

        api.get("/admin/teams")
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].memberCount").value(1))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].managerCount").value(1));
    }
}
