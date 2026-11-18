package com.bnaqica.crmdemousermanagementservice.client;

import com.bnaqica.crmdemousermanagementservice.dto.KeycloakClient;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakCredential;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakRole;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

/**
 * Keycloak Admin REST API for one realm ({@code /admin/realms/<realm>}). One instance per realm, each authenticated
 * as that realm's UMS service account; see {@code KeycloakClientConfig}.
 */
@HttpExchange(accept = "application/json")
public interface KeycloakAdminClient {

    /** Creates the user; its id (the token {@code sub}) is the last segment of the {@code Location} header. */
    @PostExchange(url = "/users", contentType = "application/json")
    ResponseEntity<Void> create(@RequestBody KeycloakUser user);

    @GetExchange("/users/{id}")
    KeycloakUser getUser(@PathVariable String id);

    @PutExchange(url = "/users/{id}", contentType = "application/json")
    void updateUser(@PathVariable String id, @RequestBody KeycloakUser user);

    @DeleteExchange("/users/{id}")
    void deleteUser(@PathVariable String id);

    @GetExchange("/users")
    List<KeycloakUser> findByEmail(@RequestParam String email, @RequestParam boolean exact);

    /** The realm's users, enabled or not; at most {@code max}. */
    @GetExchange("/users")
    List<KeycloakUser> findByEnabled(
            @RequestParam boolean enabled, @RequestParam boolean briefRepresentation, @RequestParam int max);

    /** Emails the user a link to perform the actions; the link expires after {@code lifespan} seconds. */
    @PutExchange(url = "/users/{id}/execute-actions-email", contentType = "application/json")
    void executeActionsEmail(@PathVariable String id, @RequestParam int lifespan, @RequestBody List<String> actions);

    /** Revokes all of the user's sessions. */
    @PostExchange("/users/{id}/logout")
    void logout(@PathVariable String id);

    /** Clears a brute-force lockout (attack-detection API). */
    @DeleteExchange("/attack-detection/brute-force/users/{id}")
    void clearBruteForce(@PathVariable String id);

    @GetExchange("/users/{id}/credentials")
    List<KeycloakCredential> credentials(@PathVariable String id);

    @DeleteExchange("/users/{id}/credentials/{credentialId}")
    void deleteCredential(@PathVariable String id, @PathVariable String credentialId);

    /** Realm roles the user doesn't hold yet. Needs only user permissions, unlike reading {@code /roles}. */
    @GetExchange("/users/{id}/role-mappings/realm/available")
    List<KeycloakRole> availableRealmRoles(@PathVariable String id);

    @GetExchange("/users/{id}/role-mappings/realm")
    List<KeycloakRole> realmRoles(@PathVariable String id);

    @PostExchange(url = "/users/{id}/role-mappings/realm", contentType = "application/json")
    void addRealmRoles(@PathVariable String id, @RequestBody List<KeycloakRole> roles);

    /** Needs {@code view-clients}; {@code query-clients} alone finds nothing it can't view. */
    @GetExchange("/clients")
    List<KeycloakClient> clientsByClientId(@RequestParam String clientId);

    /** The user's effective roles of one client, composites and groups included. {@code client} is its UUID. */
    @GetExchange("/users/{id}/role-mappings/clients/{client}/composite")
    List<KeycloakRole> effectiveClientRoles(@PathVariable String id, @PathVariable String client);

    /** A client's roles. {@code client} is the client's UUID. Needs {@code view-clients}. */
    @GetExchange("/clients/{client}/roles")
    List<KeycloakRole> clientRoles(@PathVariable String client, @RequestParam int max);

    /** Needs {@code manage-clients}. */
    @PostExchange(url = "/clients/{client}/roles", contentType = "application/json")
    void createClientRole(@PathVariable String client, @RequestBody KeycloakRole role);

    @GetExchange("/clients/{client}/roles/{role}/composites")
    List<KeycloakRole> clientRoleComposites(@PathVariable String client, @PathVariable String role);

    @PostExchange(url = "/clients/{client}/roles/{role}/composites", contentType = "application/json")
    void addClientRoleComposites(
            @PathVariable String client, @PathVariable String role, @RequestBody List<KeycloakRole> composites);

    @DeleteExchange(url = "/clients/{client}/roles/{role}/composites", contentType = "application/json")
    void removeClientRoleComposites(
            @PathVariable String client, @PathVariable String role, @RequestBody List<KeycloakRole> composites);

    /** The client roles mapped to the user directly. */
    @GetExchange("/users/{id}/role-mappings/clients/{client}")
    List<KeycloakRole> clientRoleMappings(@PathVariable String id, @PathVariable String client);

    @PostExchange(url = "/users/{id}/role-mappings/clients/{client}", contentType = "application/json")
    void addClientRoleMappings(
            @PathVariable String id, @PathVariable String client, @RequestBody List<KeycloakRole> roles);

    @DeleteExchange(url = "/users/{id}/role-mappings/clients/{client}", contentType = "application/json")
    void removeClientRoleMappings(
            @PathVariable String id, @PathVariable String client, @RequestBody List<KeycloakRole> roles);

    /** One realm role. Needs {@code view-realm}. */
    @GetExchange("/roles/{name}")
    KeycloakRole realmRole(@PathVariable String name);

    /** The representations need each role's id; see {@link #realmRole}. */
    @DeleteExchange(url = "/users/{id}/role-mappings/realm", contentType = "application/json")
    void removeRealmRoles(@PathVariable String id, @RequestBody List<KeycloakRole> roles);
}
