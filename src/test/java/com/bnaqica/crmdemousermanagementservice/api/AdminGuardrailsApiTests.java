package com.bnaqica.crmdemousermanagementservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** admin-access spec: admin accounts are exclusive, and the last enabled admin is protected. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class AdminGuardrailsApiTests {

    private static final Map<String, String> TICKET = Map.of("ticketRef", "OPS-30");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    private AdminApi api;
    private KeycloakTestAdmin kcAdmin;
    private String adminSub;

    @BeforeEach
    void setUp() throws Exception {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        kcAdmin = new KeycloakTestAdmin(keycloak);
        adminSub = api.adminSub();
    }

    @Test
    void crmRoleForAnAdminAccountIsRejected() throws Exception {
        api.post("/admin/users/" + adminSub + "/roles", Map.of("role", "team-member", "ticketRef", "OPS-30"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void teamMembershipForAnAdminAccountIsRejected() throws Exception {
        String team = api.createTeam("Guard " + UUID.randomUUID());

        api.post("/admin/teams/" + team + "/members", Map.of("sub", adminSub, "ticketRef", "OPS-30"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void adminDisabledWhileOthersRemain() throws Exception {
        String other =
                kcAdmin.createAdmin("other-" + UUID.randomUUID().toString().substring(0, 8) + "@crm.local");

        api.post("/admin/admins/" + other + "/disable", TICKET).andExpect(status().isOk());

        assertThat(kcAdmin.isEnabled("crm-admin", other)).isFalse();
    }

    @Test
    void disablingTheLastAdminIsAConflict() throws Exception {
        String me = adminSub;
        List<String> others =
                kcAdmin.enabledAdmins().stream().filter(id -> !id.equals(me)).toList();
        others.forEach(id -> kcAdmin.setEnabled("crm-admin", id, false));
        try {
            api.post("/admin/admins/" + me + "/disable", TICKET).andExpect(status().isConflict());
            api.delete("/admin/admins/" + me, TICKET).andExpect(status().isConflict());
        } finally {
            others.forEach(id -> kcAdmin.setEnabled("crm-admin", id, true));
        }
        assertThat(kcAdmin.isEnabled("crm-admin", me)).isTrue();
    }

    @Test
    void adminDeletedWhileOthersRemain() throws Exception {
        String email = "gone-" + UUID.randomUUID().toString().substring(0, 8) + "@crm.local";
        String other = kcAdmin.createAdmin(email);

        api.delete("/admin/admins/" + other, TICKET).andExpect(status().isNoContent());

        assertThat(kcAdmin.findByEmail("crm-admin", email)).isNull();
    }

    @Test
    void nonAdminAccountIsNotFoundHere() throws Exception {
        api.post("/admin/admins/" + UUID.randomUUID() + "/disable", TICKET).andExpect(status().isNotFound());
    }
}
