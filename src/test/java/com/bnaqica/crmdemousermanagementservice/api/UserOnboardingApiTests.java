package com.bnaqica.crmdemousermanagementservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakRole;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
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

/** user-accounts spec: creating internal users and inviting external users. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class UserOnboardingApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private KeycloakClients keycloakClients;

    private AdminApi api;

    @BeforeEach
    void setUp() {
        api = new AdminApi(mockMvc, keycloak, mailpit);
    }

    private static String unique(String domain) {
        return "u-" + UUID.randomUUID().toString().substring(0, 8) + "@" + domain;
    }

    private static Map<String, String> person(String email, String ticket) {
        return ticket == null
                ? Map.of("email", email, "firstName", "Ana", "lastName", "Test")
                : Map.of("email", email, "firstName", "Ana", "lastName", "Test", "ticketRef", ticket);
    }

    @Test
    void internalUserInvited() throws Exception {
        String email = unique("corp.example");

        var result = api.post("/admin/users/internal", person(email, "OPS-12"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userType").value("INTERNAL"))
                .andExpect(jsonPath("$.status").value("INVITED"));

        String sub = AdminApi.field(result, "sub");
        var kcUser = keycloakClients.forType(UserType.INTERNAL).getUser(sub);
        assertThat(kcUser.requiredActions()).containsExactlyInAnyOrder("UPDATE_PASSWORD", "CONFIGURE_TOTP");
        assertThat(api.emailsTo(email)).isEqualTo(1);
    }

    @Test
    void ticketReferenceMissing() throws Exception {
        String email = unique("corp.example");

        api.post("/admin/users/internal", person(email, null)).andExpect(status().isBadRequest());

        assertThat(keycloakClients.forType(UserType.INTERNAL).findByEmail(email, true))
                .isEmpty();
    }

    @Test
    void externalUserInvited() throws Exception {
        String email = unique("acme.example");
        var body = new java.util.HashMap<String, String>(person(email, "OPS-13"));
        body.put("role", "external-standard");

        var result = api.post("/admin/users/external", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userType").value("EXTERNAL"))
                .andExpect(jsonPath("$.status").value("INVITED"));

        String sub = AdminApi.field(result, "sub");
        assertThat(keycloakClients.forType(UserType.EXTERNAL).realmRoles(sub))
                .extracting(KeycloakRole::name)
                .contains("external-standard");
    }

    @Test
    void internalRoleRequestedForExternalUser() throws Exception {
        var body = new java.util.HashMap<String, String>(person(unique("acme.example"), "OPS-14"));
        body.put("role", "admin");

        api.post("/admin/users/external", body).andExpect(status().isUnprocessableContent());
    }

    @Test
    void sameEmailInTheOtherRealm() throws Exception {
        String email = unique("acme.example");
        var external = new java.util.HashMap<String, String>(person(email, "OPS-15"));
        external.put("role", "external-standard");
        api.post("/admin/users/external", external).andExpect(status().isCreated());

        // A Keycloak user created directly in the other realm, outside the UMS, also counts
        String corporate = unique("corp.example");
        keycloakClients
                .forType(UserType.EXTERNAL)
                .create(com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser.builder()
                        .username(corporate)
                        .email(corporate)
                        .enabled(true)
                        .build());

        api.post("/admin/users/internal", person(corporate.toUpperCase(), "OPS-16"))
                .andExpect(status().isConflict());
    }

    @Test
    void sameEmailAlreadyManagedByTheUms() throws Exception {
        String email = unique("corp.example");
        api.createInternal(email);

        api.post("/admin/users/internal", person(email.toUpperCase(), "OPS-17")).andExpect(status().isConflict());
    }

    @Test
    void corporateDomainUsedForExternalUser() throws Exception {
        var body = new java.util.HashMap<String, String>(person(unique("corp.example"), "OPS-18"));
        body.put("role", "external-standard");

        api.post("/admin/users/external", body).andExpect(status().isUnprocessableContent());
    }

    @Test
    void nonCorporateDomainUsedForInternalUser() throws Exception {
        api.post("/admin/users/internal", person(unique("acme.example"), "OPS-19"))
                .andExpect(status().isUnprocessableContent());
    }
}
