package com.bnaqica.crmdemousermanagementservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** teams spec: internal users only, the multi-team flag, managers, archived teams. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class TeamMembershipApiTests {

    private static final Map<String, String> TICKET = Map.of("ticketRef", "OPS-9");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private UserAccountService accounts;

    private AdminApi api;
    private String teamA;
    private String teamB;
    private String sub;

    @BeforeEach
    void setUp() throws Exception {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        teamA = api.createTeam("A " + UUID.randomUUID());
        teamB = api.createTeam("B " + UUID.randomUUID());
        sub = api.createInternal(uniqueEmail("corp.example"));
    }

    private static String uniqueEmail(String domain) {
        return "t-" + UUID.randomUUID().toString().substring(0, 8) + "@" + domain;
    }

    private org.springframework.test.web.servlet.ResultActions add(String team, String user) throws Exception {
        return api.post("/admin/teams/" + team + "/members", Map.of("sub", user, "ticketRef", "OPS-9"));
    }

    private void allowMultiTeam(boolean allowed) throws Exception {
        api.put("/admin/users/" + sub + "/multi-team", Map.of("allowed", allowed, "ticketRef", "OPS-9"))
                .andExpect(status().isOk());
    }

    @Test
    void memberAdded() throws Exception {
        add(teamA, sub)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sub").value(sub))
                .andExpect(jsonPath("$.manager").value(false));

        api.get("/admin/teams/" + teamA + "/members")
                .andExpect(jsonPath("$[0].sub").value(sub));
    }

    @Test
    void addingTheSameMemberTwiceIsAConflict() throws Exception {
        add(teamA, sub).andExpect(status().isCreated());

        add(teamA, sub).andExpect(status().isConflict());
    }

    @Test
    void memberAddedToArchivedTeamIsAConflict() throws Exception {
        api.post("/admin/teams/" + teamA + "/archive", TICKET).andExpect(status().isOk());

        add(teamA, sub).andExpect(status().isConflict());
    }

    @Test
    void externalUserAddedToTeamIsRejected() throws Exception {
        String external = AdminApi.field(
                api.post(
                        "/admin/users/external",
                        Map.of(
                                "email", uniqueEmail("acme.example"),
                                "firstName", "Pat",
                                "lastName", "Ext",
                                "role", "external-standard",
                                "ticketRef", "OPS-9")),
                "sub");

        add(teamA, external).andExpect(status().isUnprocessableContent());
    }

    @Test
    void disabledUserAddedToTeamIsRejected() throws Exception {
        UserAccount account = accounts.get(UUID.fromString(sub));
        account.setStatus(UserStatus.DISABLED);
        accounts.update(account);

        add(teamA, sub).andExpect(status().isUnprocessableContent());
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        add(teamA, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    void secondTeamWithoutFlagIsRejected() throws Exception {
        add(teamA, sub).andExpect(status().isCreated());

        add(teamB, sub).andExpect(status().isUnprocessableContent());
    }

    @Test
    void secondTeamAsManagerWithoutFlagIsRejected() throws Exception {
        add(teamA, sub).andExpect(status().isCreated());

        api.post("/admin/teams/" + teamB + "/members", Map.of("sub", sub, "manager", true, "ticketRef", "OPS-9"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void secondTeamWithFlagIsAllowed() throws Exception {
        allowMultiTeam(true);
        add(teamA, sub).andExpect(status().isCreated());

        add(teamB, sub).andExpect(status().isCreated());
    }

    @Test
    void concurrentAddsToTwoTeamsLetOnlyOneThrough() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> adds = List.of(teamA, teamB).stream()
                .map(team -> CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                        return add(team, sub).andReturn().getResponse().getStatus();
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                }))
                .toList();
        start.countDown();

        List<Integer> statuses =
                adds.stream().map(CompletableFuture::join).sorted().toList();
        assertThat(statuses).containsExactly(201, 422);
    }

    @Test
    void turningTheFlagOffWhileInTwoTeamsIsAConflict() throws Exception {
        allowMultiTeam(true);
        add(teamA, sub).andExpect(status().isCreated());
        add(teamB, sub).andExpect(status().isCreated());

        api.put("/admin/users/" + sub + "/multi-team", Map.of("allowed", false, "ticketRef", "OPS-9"))
                .andExpect(status().isConflict());
    }

    @Test
    void turningTheFlagOffInOneTeamIsAllowed() throws Exception {
        allowMultiTeam(true);
        add(teamA, sub).andExpect(status().isCreated());

        api.put("/admin/users/" + sub + "/multi-team", Map.of("allowed", false, "ticketRef", "OPS-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.multiTeamAllowed").value(false));
    }

    @Test
    void flagChangeWithoutTicketIsRejected() throws Exception {
        api.put("/admin/users/" + sub + "/multi-team", Map.of("allowed", true)).andExpect(status().isBadRequest());
    }

    @Test
    void memberRemoved() throws Exception {
        add(teamA, sub).andExpect(status().isCreated());

        api.delete("/admin/teams/" + teamA + "/members/" + sub, TICKET).andExpect(status().isNoContent());

        api.get("/admin/teams/" + teamA + "/members").andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void removingSomeoneNotInTheTeamIsNotFound() throws Exception {
        api.delete("/admin/teams/" + teamA + "/members/" + sub, TICKET).andExpect(status().isNotFound());
    }

    @Test
    void managerSetAndUnset() throws Exception {
        add(teamA, sub).andExpect(status().isCreated());

        api.put("/admin/teams/" + teamA + "/members/" + sub, Map.of("manager", true, "ticketRef", "OPS-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manager").value(true));
        api.put("/admin/teams/" + teamA + "/members/" + sub, Map.of("manager", false, "ticketRef", "OPS-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manager").value(false));
    }

    @Test
    void managerOfSomeoneNotInTheTeamIsNotFound() throws Exception {
        api.put("/admin/teams/" + teamA + "/members/" + sub, Map.of("manager", true, "ticketRef", "OPS-9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void disabledMemberCannotBeMadeManager() throws Exception {
        add(teamA, sub).andExpect(status().isCreated());
        UserAccount account = accounts.get(UUID.fromString(sub));
        account.setStatus(UserStatus.DISABLED);
        accounts.update(account);

        api.put("/admin/teams/" + teamA + "/members/" + sub, Map.of("manager", true, "ticketRef", "OPS-9"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void membersListedWithEmailAndName() throws Exception {
        add(teamA, sub).andExpect(status().isCreated());

        api.get("/admin/teams/" + teamA + "/members")
                .andExpect(jsonPath("$[0].email")
                        .value(accounts.get(UUID.fromString(sub)).getEmail()))
                .andExpect(jsonPath("$[0].firstName").value("Ana"))
                .andExpect(jsonPath("$[0].lastName").value("Test"));
    }

    @Test
    void usersTeamsListed() throws Exception {
        api.post("/admin/teams/" + teamA + "/members", Map.of("sub", sub, "manager", true, "ticketRef", "OPS-9"))
                .andExpect(status().isCreated());

        api.get("/admin/users/" + sub + "/teams")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value(teamA))
                .andExpect(jsonPath("$[0].teamName").isNotEmpty())
                .andExpect(jsonPath("$[0].manager").value(true));
    }

    @Test
    void userShowsTheMultiTeamFlag() throws Exception {
        api.get("/admin/users/" + sub).andExpect(jsonPath("$.multiTeamAllowed").value(false));

        allowMultiTeam(true);

        api.get("/admin/users/" + sub).andExpect(jsonPath("$.multiTeamAllowed").value(true));
    }

    @Test
    void managerSetOnArchivedTeamIsAConflict() throws Exception {
        add(teamA, sub).andExpect(status().isCreated());
        api.post("/admin/teams/" + teamA + "/archive", TICKET).andExpect(status().isOk());

        api.put("/admin/teams/" + teamA + "/members/" + sub, Map.of("manager", true, "ticketRef", "OPS-9"))
                .andExpect(status().isConflict());
    }

    @Test
    void archivedTeamCanStillBeEmptied() throws Exception {
        api.post("/admin/teams/" + teamA + "/members", Map.of("sub", sub, "manager", true, "ticketRef", "OPS-9"))
                .andExpect(status().isCreated());
        api.post("/admin/teams/" + teamA + "/archive", TICKET).andExpect(status().isOk());

        api.put("/admin/teams/" + teamA + "/members/" + sub, Map.of("manager", false, "ticketRef", "OPS-9"))
                .andExpect(status().isOk());
        api.delete("/admin/teams/" + teamA + "/members/" + sub, TICKET).andExpect(status().isNoContent());
    }

    @Test
    void multiTeamFlagOnExternalUserIsRejected() throws Exception {
        String external = AdminApi.field(
                api.post(
                        "/admin/users/external",
                        Map.of(
                                "email", uniqueEmail("acme.example"),
                                "firstName", "Pat",
                                "lastName", "Ext",
                                "role", "external-standard",
                                "ticketRef", "OPS-9")),
                "sub");

        api.put("/admin/users/" + external + "/multi-team", Map.of("allowed", true, "ticketRef", "OPS-9"))
                .andExpect(status().isUnprocessableContent());
    }
}
