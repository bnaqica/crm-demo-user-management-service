package com.bnaqica.crmdemousermanagementservice.support;

import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Test-only setup through Keycloak's master admin: things the UMS itself never does, such as setting a password,
 * signing a user in, or giving them an OTP credential.
 */
public class KeycloakTestAdmin {

    private final KeycloakContainer keycloak;
    private final RestClient rest = RestClient.create();

    public KeycloakTestAdmin(KeycloakContainer keycloak) {
        this.keycloak = keycloak;
    }

    private String masterToken() {
        Map<?, ?> response = rest.post()
                .uri(keycloak.tokenUri("master"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=password&client_id=admin-cli&username=admin&password=admin")
                .retrieve()
                .body(Map.class);
        return (String) response.get("access_token");
    }

    private String users(String realm) {
        return keycloak.baseUrl() + "/admin/realms/" + realm + "/users";
    }

    /** Sets a password and clears required actions, so the user can sign in with a password grant. */
    public void makeLoginReady(String realm, String id) {
        rest.put()
                .uri(users(realm) + "/" + id + "/reset-password")
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", TestTokens.PASSWORD, "temporary", false))
                .retrieve()
                .toBodilessEntity();
        rest.put()
                .uri(users(realm) + "/" + id)
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requiredActions", List.of(), "emailVerified", true))
                .retrieve()
                .toBodilessEntity();
    }

    /** Signs in with a password grant; returns the HTTP status. */
    public int login(String realm, String username, String password) {
        return rest.post()
                .uri(keycloak.tokenUri(realm))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=password&client_id=test-client&username=" + username + "&password=" + password)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    public int sessionCount(String realm, String id) {
        List<?> sessions = rest.get()
                .uri(users(realm) + "/" + id + "/sessions")
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        return sessions.size();
    }

    @SuppressWarnings("unchecked")
    public boolean isLocked(String realm, String id) {
        Map<String, Object> status = rest.get()
                .uri(keycloak.baseUrl() + "/admin/realms/" + realm + "/attack-detection/brute-force/users/" + id)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(Map.class);
        return Boolean.TRUE.equals(status.get("disabled"));
    }

    /** Imports a user that already has a password and a TOTP credential; returns its id. */
    public String importUserWithOtp(String realm, String email) {
        var otp = Map.of(
                "type", "otp",
                "userLabel", "phone",
                "secretData", "{\"value\":\"JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP\"}",
                "credentialData",
                        "{\"subType\":\"totp\",\"digits\":6,\"counter\":0,\"period\":30,\"algorithm\":\"HmacSHA1\"}");
        var user = Map.of(
                "username", email, "email", email, "enabled", true, "emailVerified", true, "credentials", List.of(otp));
        rest.post()
                .uri(keycloak.baseUrl() + "/admin/realms/" + realm + "/partialImport")
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ifResourceExists", "FAIL", "users", List.of(user)))
                .retrieve()
                .toBodilessEntity();
        List<Map<String, Object>> found = rest.get()
                .uri(users(realm) + "?exact=true&email=" + email)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        return (String) found.getFirst().get("id");
    }
}
