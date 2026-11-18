package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakRole;
import com.bnaqica.crmdemousermanagementservice.entity.Permission;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Keeps Keycloak in line with the catalog and the roles. Permissions are client roles of the realm's permission
 * client ({@code crm-api} or {@code portal-app}); a UMS role is a composite client role of that client holding its
 * permissions. Every method is idempotent, so a retry after a failure completes the job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleKeycloakSync {

    private static final int MAX_ROLES = 10_000;

    private final KeycloakClients keycloak;
    private final KeycloakClientIds clientIds;

    /** Creates the client roles that are missing for these permissions. */
    public void ensurePermissions(UserType realm, Collection<Permission> permissions) {
        ensureClientRoles(
                realm,
                permissions.stream()
                        .map(permission -> new KeycloakRole(null, permission.getKey(), permission.getDescription()))
                        .toList());
    }

    /** Creates the role if missing and makes its composites exactly {@code permissionKeys}. */
    public void syncRole(Role role, Set<String> permissionKeys) {
        try {
            syncOnce(role, permissionKeys);
        } catch (KeycloakClientException ex) {
            if (!ex.isConflict()) {
                throw ex;
            }
            // Another instance changed the same composite at the same moment; look again and finish the job
            syncOnce(role, permissionKeys);
        }
    }

    private void syncOnce(Role role, Set<String> permissionKeys) {
        UserType realm = role.getAssignableTo();
        ensureClientRoles(realm, List.of(new KeycloakRole(null, role.getName(), role.getDescription())));
        KeycloakAdminClient client = keycloak.forType(realm);
        String clientId = clientIds.permissionClientId(realm);
        Set<String> current = client.clientRoleComposites(clientId, role.getName()).stream()
                .map(KeycloakRole::name)
                .collect(Collectors.toSet());
        var byName = client.clientRoles(clientId, MAX_ROLES).stream()
                .collect(Collectors.toMap(KeycloakRole::name, Function.identity()));
        List<String> missing = permissionKeys.stream()
                .filter(key -> !byName.containsKey(key))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new KeycloakClientException(404, "Keycloak lacks the client roles " + missing);
        }
        List<KeycloakRole> toAdd = permissionKeys.stream()
                .filter(key -> !current.contains(key))
                .map(byName::get)
                .toList();
        List<KeycloakRole> toRemove = current.stream()
                .filter(name -> !permissionKeys.contains(name))
                .map(byName::get)
                .toList();
        if (!toAdd.isEmpty()) {
            client.addClientRoleComposites(clientId, role.getName(), toAdd);
        }
        if (!toRemove.isEmpty()) {
            client.removeClientRoleComposites(clientId, role.getName(), toRemove);
        }
        log.info("Synced role {} in Keycloak: {} added, {} removed", role.getName(), toAdd.size(), toRemove.size());
    }

    /**
     * The UMS roles of {@code candidates} the user holds directly. The built-in portal roles are realm roles (the
     * portal reads those); every other role is a client role of the realm's permission client.
     */
    public Set<String> heldRoles(UserAccount user, Collection<Role> candidates) {
        KeycloakAdminClient client = keycloak.forType(user.getUserType());
        String sub = user.getSub().toString();
        Set<String> held = client.clientRoleMappings(sub, clientIds.permissionClientId(user.getUserType())).stream()
                .map(KeycloakRole::name)
                .collect(Collectors.toSet());
        List<Role> realmRoles =
                candidates.stream().filter(RoleKeycloakSync::isRealmRole).toList();
        if (!realmRoles.isEmpty()) {
            // Reading realm mappings needs view-realm; what isn't available to add is held
            Set<String> available = client.availableRealmRoles(sub).stream()
                    .map(KeycloakRole::name)
                    .collect(Collectors.toSet());
            realmRoles.stream()
                    .map(Role::getName)
                    .filter(name -> !available.contains(name))
                    .forEach(held::add);
        }
        Set<String> names = candidates.stream().map(Role::getName).collect(Collectors.toSet());
        held.retainAll(names);
        return held;
    }

    public void assign(UserAccount user, Role role) {
        KeycloakAdminClient client = keycloak.forType(user.getUserType());
        String sub = user.getSub().toString();
        if (isRealmRole(role)) {
            KeycloakRole realmRole = client.availableRealmRoles(sub).stream()
                    .filter(found -> found.name().equals(role.getName()))
                    .findFirst()
                    .orElseThrow(() -> new KeycloakClientException(404, "No realm role " + role.getName()));
            client.addRealmRoles(sub, List.of(realmRole));
        } else {
            String clientId = clientIds.permissionClientId(user.getUserType());
            KeycloakRole clientRole = findClientRole(client, clientId, role.getName())
                    .orElseGet(() -> {
                        // A built-in role is first created in Keycloak when it is first needed
                        syncRole(role, RoleRules.keys(role));
                        return clientRole(client, clientId, role.getName());
                    });
            client.addClientRoleMappings(sub, clientId, List.of(clientRole));
        }
    }

    public void revoke(UserAccount user, Role role) {
        KeycloakAdminClient client = keycloak.forType(user.getUserType());
        String sub = user.getSub().toString();
        if (isRealmRole(role)) {
            client.removeRealmRoles(sub, List.of(client.realmRole(role.getName())));
        } else {
            String clientId = clientIds.permissionClientId(user.getUserType());
            client.removeClientRoleMappings(sub, clientId, List.of(clientRole(client, clientId, role.getName())));
        }
    }

    /** {@code external-starter} and {@code external-standard}: realm roles, composites of the same-named client role. */
    static boolean isRealmRole(Role role) {
        return role.isBuiltIn() && role.getAssignableTo() == UserType.EXTERNAL;
    }

    private static KeycloakRole clientRole(KeycloakAdminClient client, String clientId, String name) {
        return findClientRole(client, clientId, name)
                .orElseThrow(() -> new KeycloakClientException(404, "No client role " + name));
    }

    private static java.util.Optional<KeycloakRole> findClientRole(
            KeycloakAdminClient client, String clientId, String name) {
        return client.clientRoles(clientId, MAX_ROLES).stream()
                .filter(found -> found.name().equals(name))
                .findFirst();
    }

    private void ensureClientRoles(UserType realm, List<KeycloakRole> wanted) {
        KeycloakAdminClient client = keycloak.forType(realm);
        String clientId = clientIds.permissionClientId(realm);
        Set<String> existing = client.clientRoles(clientId, MAX_ROLES).stream()
                .map(KeycloakRole::name)
                .collect(Collectors.toSet());
        wanted.stream().filter(role -> !existing.contains(role.name())).forEach(role -> {
            try {
                client.createClientRole(clientId, role);
                log.info("Created client role {} in {}", role.name(), realm.getRealm());
            } catch (KeycloakClientException ex) {
                if (!ex.isConflict()) {
                    throw ex;
                }
                log.debug("Client role {} was created at the same time elsewhere", role.name());
            }
        });
    }
}
