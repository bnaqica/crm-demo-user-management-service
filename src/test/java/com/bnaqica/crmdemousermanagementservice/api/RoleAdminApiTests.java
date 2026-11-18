package com.bnaqica.crmdemousermanagementservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.CatalogFixture;
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
import org.springframework.test.web.servlet.ResultActions;

/** roles spec, "Role definitions", and permission-catalog spec, "Write includes read". */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class RoleAdminApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    private AdminApi api;
    private KeycloakTestAdmin kcAdmin;
    private String res;
    private String name;

    @BeforeEach
    void setUp() throws Exception {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        kcAdmin = new KeycloakTestAdmin(keycloak);
        res = "r" + UUID.randomUUID().toString().substring(0, 8);
        name = "role-" + UUID.randomUUID().toString().substring(0, 8);
        new CatalogFixture(mockMvc, keycloak)
                .register(List.of(
                        Map.of(res + ":read", "crm-internal"),
                        Map.of(res + ":write", "crm-internal"),
                        Map.of(res + "-old:read", "crm-internal"),
                        Map.of("portal" + res + ":read", "portal")));
    }

    private ResultActions create(List<String> permissions) throws Exception {
        return api.post(
                "/admin/roles",
                Map.of(
                        "name", name,
                        "description", "Test role",
                        "owner", "sales-ops",
                        "assignableTo", "INTERNAL",
                        "permissions", permissions,
                        "ticketRef", "OPS-5"));
    }

    @Test
    void roleCreatedAsKeycloakComposite() throws Exception {
        create(List.of(res + ":read"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.owner").value("sales-ops"))
                .andExpect(jsonPath("$.permissions").value(containsInAnyOrder(res + ":read")));

        assertThat(kcAdmin.clientRoleComposites("crm-internal", "crm-api", name))
                .containsExactly(res + ":read");
    }

    @Test
    void writeIncludesRead() throws Exception {
        create(List.of(res + ":write"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissions").value(containsInAnyOrder(res + ":read", res + ":write")));

        assertThat(kcAdmin.clientRoleComposites("crm-internal", "crm-api", name))
                .containsExactlyInAnyOrder(res + ":read", res + ":write");
    }

    @Test
    void mixedRealmPermissionsAreRejected() throws Exception {
        create(List.of(res + ":read", "portal" + res + ":read")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void unknownPermissionIsRejected() throws Exception {
        create(List.of("nothing" + res + ":read")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void deprecatedPermissionIsNotGrantable() throws Exception {
        new CatalogFixture(mockMvc, keycloak).registerCrm(res + ":read", res + ":write");

        create(List.of(res + "-old:read")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void duplicateNameIsAConflict() throws Exception {
        create(List.of(res + ":read")).andExpect(status().isCreated());

        create(List.of(res + ":read")).andExpect(status().isConflict());
    }

    @Test
    void nameMustBeKeycloakSafe() throws Exception {
        name = "Sales Role";
        create(List.of(res + ":read")).andExpect(status().isBadRequest());
    }

    @Test
    void roleEditUpdatesTheComposite() throws Exception {
        create(List.of(res + ":read")).andExpect(status().isCreated());

        api.put(
                        "/admin/roles/" + name,
                        Map.of(
                                "description", "Edited",
                                "owner", "ops",
                                "permissions", List.of(res + ":write"),
                                "ticketRef", "OPS-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Edited"));

        assertThat(kcAdmin.clientRoleComposites("crm-internal", "crm-api", name))
                .containsExactlyInAnyOrder(res + ":read", res + ":write");
    }

    @Test
    void permissionRemovedFromRoleLeavesTheComposite() throws Exception {
        create(List.of(res + ":write")).andExpect(status().isCreated());

        api.put(
                        "/admin/roles/" + name,
                        Map.of("description", "d", "owner", "ops", "permissions", List.of(), "ticketRef", "OPS-6"))
                .andExpect(status().isOk());

        assertThat(kcAdmin.clientRoleComposites("crm-internal", "crm-api", name))
                .isEmpty();
    }

    @Test
    void portalRoleIsAPortalAppComposite() throws Exception {
        api.post(
                        "/admin/roles",
                        Map.of(
                                "name", name,
                                "description", "Portal role",
                                "owner", "portal-ops",
                                "assignableTo", "EXTERNAL",
                                "permissions", List.of("portal" + res + ":read"),
                                "ticketRef", "OPS-5"))
                .andExpect(status().isCreated());

        assertThat(kcAdmin.clientRoleComposites("portal", "portal-app", name))
                .containsExactly("portal" + res + ":read");
    }

    @Test
    void roleWithoutTicketIsRejected() throws Exception {
        api.post(
                        "/admin/roles",
                        Map.of(
                                "name",
                                name,
                                "description",
                                "d",
                                "owner",
                                "o",
                                "assignableTo",
                                "INTERNAL",
                                "permissions",
                                List.of()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoleIsNotFound() throws Exception {
        api.get("/admin/roles/no-such-role").andExpect(status().isNotFound());
    }

    @Test
    void superManagerCannotLoseANonSensitivePermission() throws Exception {
        api.put(
                        "/admin/roles/super-manager",
                        Map.of("description", "d", "owner", "ums", "permissions", List.of(), "ticketRef", "OPS-6"))
                .andExpect(status().isUnprocessableContent());

        assertThat(kcAdmin.clientRoleComposites("crm-internal", "crm-api", "super-manager"))
                .contains(res + ":read");
    }
}
