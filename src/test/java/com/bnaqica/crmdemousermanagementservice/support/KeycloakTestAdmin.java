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

    /** Gives the user a client role of {@code clientId}, creating the role first if it doesn't exist. */
    @SuppressWarnings("unchecked")
    public void grantClientRole(String realm, String userId, String clientId, String role) {
        String admin = keycloak.baseUrl() + "/admin/realms/" + realm;
        List<Map<String, Object>> clients = rest.get()
                .uri(admin + "/clients?clientId=" + clientId)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        String clientUuid = (String) clients.getFirst().get("id");
        String roles = admin + "/clients/" + clientUuid + "/roles";
        rest.post()
                .uri(roles)
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", role))
                .exchange((request, response) -> response.getStatusCode().value()); // 409 if it exists already
        Map<String, Object> representation = rest.get()
                .uri(roles + "/" + role)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(Map.class);
        rest.post()
                .uri(users(realm) + "/" + userId + "/role-mappings/clients/" + clientUuid)
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(representation))
                .retrieve()
                .toBodilessEntity();
    }

    /** Names of a client's roles. */
    @SuppressWarnings("unchecked")
    public List<String> clientRoleNames(String realm, String clientId) {
        String admin = keycloak.baseUrl() + "/admin/realms/" + realm;
        List<Map<String, Object>> clients = rest.get()
                .uri(admin + "/clients?clientId=" + clientId)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        List<Map<String, Object>> roles = rest.get()
                .uri(admin + "/clients/" + clients.getFirst().get("id") + "/roles?max=1000")
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        return roles.stream().map(role -> (String) role.get("name")).toList();
    }

    /** Names of the roles inside a client role. */
    @SuppressWarnings("unchecked")
    public List<String> clientRoleComposites(String realm, String clientId, String role) {
        String admin = keycloak.baseUrl() + "/admin/realms/" + realm;
        List<Map<String, Object>> clients = rest.get()
                .uri(admin + "/clients?clientId=" + clientId)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        List<Map<String, Object>> composites = rest.get()
                .uri(admin + "/clients/" + clients.getFirst().get("id") + "/roles/" + role + "/composites")
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        return composites.stream().map(found -> (String) found.get("name")).toList();
    }

    /** Names of the client roles mapped to the user directly. */
    @SuppressWarnings("unchecked")
    public List<String> clientRoleMappings(String realm, String userId, String clientId) {
        String admin = keycloak.baseUrl() + "/admin/realms/" + realm;
        List<Map<String, Object>> clients = rest.get()
                .uri(admin + "/clients?clientId=" + clientId)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        List<Map<String, Object>> roles = rest.get()
                .uri(users(realm) + "/" + userId + "/role-mappings/clients/"
                        + clients.getFirst().get("id"))
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        return roles.stream().map(role -> (String) role.get("name")).toList();
    }

    /** Names of the realm roles mapped to the user directly. */
    @SuppressWarnings("unchecked")
    public List<String> realmRoleNames(String realm, String userId) {
        List<Map<String, Object>> roles = rest.get()
                .uri(users(realm) + "/" + userId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        return roles.stream().map(role -> (String) role.get("name")).toList();
    }

    /** The user with this email, or null. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> findByEmail(String realm, String email) {
        List<Map<String, Object>> found = rest.get()
                .uri(users(realm) + "?exact=true&email=" + email)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Creates an enabled crm-admin account holding the realm role {@code admin}; returns its id. */
    @SuppressWarnings("unchecked")
    public String createAdmin(String email) {
        String admin = keycloak.baseUrl() + "/admin/realms/crm-admin";
        rest.post()
                .uri(users("crm-admin"))
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", email, "email", email, "enabled", true))
                .retrieve()
                .toBodilessEntity();
        String id = (String) findByEmail("crm-admin", email).get("id");
        Map<String, Object> role = rest.get()
                .uri(admin + "/roles/admin")
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(Map.class);
        rest.post()
                .uri(users("crm-admin") + "/" + id + "/role-mappings/realm")
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
        return id;
    }

    /** Ids of the enabled users holding the crm-admin realm role {@code admin}. */
    @SuppressWarnings("unchecked")
    public List<String> enabledAdmins() {
        List<Map<String, Object>> found = rest.get()
                .uri(keycloak.baseUrl() + "/admin/realms/crm-admin/roles/admin/users?max=1000")
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(List.class);
        return found.stream()
                .filter(user -> Boolean.TRUE.equals(user.get("enabled")))
                .map(user -> (String) user.get("id"))
                .toList();
    }

    public void setEnabled(String realm, String id, boolean enabled) {
        rest.put()
                .uri(users(realm) + "/" + id)
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    public boolean isEnabled(String realm, String id) {
        Map<String, Object> user = rest.get()
                .uri(users(realm) + "/" + id)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(Map.class);
        return Boolean.TRUE.equals(user.get("enabled"));
    }

    @SuppressWarnings("unchecked")
    public void grantRealmRole(String realm, String userId, String role) {
        Map<String, Object> representation = rest.get()
                .uri(keycloak.baseUrl() + "/admin/realms/" + realm + "/roles/" + role)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(Map.class);
        rest.post()
                .uri(users(realm) + "/" + userId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(representation))
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    public void removeRealmRole(String realm, String userId, String role) {
        Map<String, Object> representation = rest.get()
                .uri(keycloak.baseUrl() + "/admin/realms/" + realm + "/roles/" + role)
                .header("Authorization", "Bearer " + masterToken())
                .retrieve()
                .body(Map.class);
        rest.method(org.springframework.http.HttpMethod.DELETE)
                .uri(users(realm) + "/" + userId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(representation))
                .retrieve()
                .toBodilessEntity();
    }

    /** Creates a plain enabled user, as a self-registration would; returns its id. */
    public String createUser(String realm, String email) {
        rest.post()
                .uri(users(realm))
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username",
                        email,
                        "email",
                        email,
                        "enabled",
                        true,
                        "firstName",
                        "Self",
                        "lastName",
                        "Registered"))
                .retrieve()
                .toBodilessEntity();
        return (String) findByEmail(realm, email).get("id");
    }

    public boolean userExists(String realm, String id) {
        return rest.get()
                        .uri(users(realm) + "/" + id)
                        .header("Authorization", "Bearer " + masterToken())
                        .exchange(
                                (request, response) -> response.getStatusCode().value())
                == 200;
    }

    /** As {@link #createUser(String, String)}, with required actions, as an invite leaves the account. */
    public String createUser(String realm, String email, List<String> requiredActions) {
        rest.post()
                .uri(users(realm))
                .header("Authorization", "Bearer " + masterToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", email, "email", email, "enabled", true, "requiredActions", requiredActions))
                .retrieve()
                .toBodilessEntity();
        return (String) findByEmail(realm, email).get("id");
    }
}
