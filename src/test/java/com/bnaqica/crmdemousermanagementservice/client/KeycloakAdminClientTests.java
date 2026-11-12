package com.bnaqica.crmdemousermanagementservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakCredential;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakRole;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Exercises every Keycloak Admin REST call the UMS makes, against a real Keycloak with the test realms. */
@SpringBootTest
@Import(IntegrationTestContainers.class)
class KeycloakAdminClientTests {

    @Autowired
    private KeycloakClients clients;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private KeycloakContainer keycloak;

    private final String email = "kc-" + UUID.randomUUID() + "@corp.example";

    private KeycloakAdminClient internal() {
        return clients.forType(UserType.INTERNAL);
    }

    private String createUser(KeycloakAdminClient client) {
        var location = client.create(KeycloakUser.builder()
                        .username(email)
                        .email(email)
                        .firstName("Ana")
                        .lastName("Test")
                        .enabled(true)
                        .requiredActions(List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP"))
                        .build())
                .getHeaders()
                .getLocation()
                .getPath();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void createsAndReadsUser() {
        String id = createUser(internal());

        KeycloakUser user = internal().getUser(id);

        assertThat(user.email()).isEqualTo(email);
        assertThat(user.requiredActions()).containsExactlyInAnyOrder("UPDATE_PASSWORD", "CONFIGURE_TOTP");
    }

    @Test
    void findsUserByExactEmail() {
        String id = createUser(internal());

        assertThat(internal().findByEmail(email, true))
                .extracting(KeycloakUser::id)
                .containsExactly(id);
    }

    @Test
    void updatesOnlyGivenFields() {
        String id = createUser(internal());

        internal().updateUser(id, KeycloakUser.builder().enabled(false).build());

        KeycloakUser user = internal().getUser(id);
        assertThat(user.enabled()).isFalse();
        assertThat(user.firstName()).isEqualTo("Ana");
    }

    @Test
    void sendsActionsEmail() {
        String id = createUser(internal());

        internal().executeActionsEmail(id, 3600, List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP"));

        Map<?, ?> search = RestClient.create()
                .get()
                .uri(mailpit.apiUrl() + "/search?query=to:" + email)
                .retrieve()
                .body(Map.class);
        assertThat(((Number) search.get("messages_count")).intValue()).isEqualTo(1);
    }

    @Test
    void logsOutClearsLockoutAndListsCredentials() {
        String id = createUser(internal());

        internal().logout(id);
        internal().clearBruteForce(id);
        List<KeycloakCredential> credentials = internal().credentials(id);

        assertThat(credentials).isEmpty();
    }

    @Test
    void assignsRealmRoleInPortal() {
        KeycloakAdminClient portal = clients.forType(UserType.EXTERNAL);
        String id = createUser(portal);
        KeycloakRole role = portal.availableRealmRoles(id).stream()
                .filter(r -> r.name().equals("external-standard"))
                .findFirst()
                .orElseThrow();

        portal.addRealmRoles(id, List.of(role));

        assertThat(portal.realmRoles(id)).extracting(KeycloakRole::name).contains("external-standard");
    }

    @Test
    void serviceAccountCannotChangeRealmSettings() {
        String token = RestClient.create()
                .post()
                .uri(keycloak.tokenUri("crm-internal"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&client_id=ums-internal&client_secret=ums-internal-local-secret")
                .retrieve()
                .body(Map.class)
                .get("access_token")
                .toString();

        var response = RestClient.create()
                .put()
                .uri(keycloak.baseUrl() + "/admin/realms/crm-internal")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("registrationAllowed", true))
                .exchange((request, res) -> res.getStatusCode().value());

        assertThat(response).isEqualTo(403);
    }

    @Test
    void deletesUser() {
        String id = createUser(internal());

        internal().deleteUser(id);

        assertThatThrownBy(() -> internal().getUser(id))
                .isInstanceOfSatisfying(
                        KeycloakClientException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(404));
    }

    @Test
    void duplicateUserIsAConflict() {
        createUser(internal());

        assertThatThrownBy(() -> createUser(internal()))
                .isInstanceOfSatisfying(
                        KeycloakClientException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(409));
    }
}
