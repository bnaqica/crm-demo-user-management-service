package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.CatalogSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.request.CatalogRegistrationRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.CatalogRegistrationRequest.Entry;
import com.bnaqica.crmdemousermanagementservice.dto.response.CatalogRegistrationResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.PermissionResponse;
import com.bnaqica.crmdemousermanagementservice.entity.Permission;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.CatalogConflictException;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidUserRequestException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * A registration is stored and audited in one transaction, Super Manager gaining any new non-sensitive CRM
 * permission. Keycloak is brought in line afterwards; that step is idempotent, so the next registration repairs a
 * failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCatalogServiceImpl implements PermissionCatalogService {

    private final PermissionService permissions;
    private final RoleService roles;
    private final RoleKeycloakSync keycloakSync;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Override
    public CatalogRegistrationResponse register(String service, CatalogRegistrationRequest request) {
        Map<String, Entry> wanted = byRealmAndKey(request.permissions());
        Registered registered = transactions.execute(status -> store(service, wanted));
        for (UserType realm : UserType.values()) {
            keycloakSync.ensurePermissions(
                    realm,
                    registered.active().stream()
                            .filter(permission -> permission.getRealm() == realm)
                            .toList());
        }
        keycloakSync.syncRole(registered.superManager(), RoleRules.keys(registered.superManager()));
        log.info("Registered {} permissions for {}", registered.active().size(), service);
        return new CatalogRegistrationResponse(service, keys(registered.active()), keys(registered.deprecated()));
    }

    private Registered store(String service, Map<String, Entry> wanted) {
        Instant now = Instant.now(clock);
        // Every registration locks Super Manager first, so concurrent ones (replicas, several services) take turns
        Role superManager = roles.lockForUpdate(RoleService.SUPER_MANAGER);
        List<Permission> before = permissions.findByService(service);
        Set<UUID> turnedSensitive = new HashSet<>();
        CatalogSnapshot beforeSnapshot = snapshot(before);
        List<Permission> active = new ArrayList<>();
        for (Entry entry : wanted.values()) {
            UserType realm = UserType.ofRealm(entry.realm()).orElseThrow();
            Optional<Permission> existing = permissions.find(realm, entry.key());
            Permission permission = existing.orElseGet(() -> Permission.builder()
                    .id(UUID.randomUUID())
                    .key(entry.key())
                    .service(service)
                    .realm(realm)
                    .createdAt(now)
                    .build());
            if (!permission.getService().equals(service)) {
                throw new CatalogConflictException(
                        entry.key() + " is registered by " + permission.getService() + " already");
            }
            if (existing.isPresent() && !permission.isSensitive() && entry.sensitive()) {
                turnedSensitive.add(permission.getId());
            }
            permission.setDescription(entry.description());
            permission.setSensitive(entry.sensitive());
            permission.setDeprecatedAt(null);
            active.add(permissions.save(permission));
        }
        Set<UUID> activeIds = active.stream().map(Permission::getId).collect(Collectors.toSet());
        List<Permission> deprecated = new ArrayList<>();
        for (Permission old : before) {
            if (!activeIds.contains(old.getId())) {
                if (!old.isDeprecated()) {
                    old.setDeprecatedAt(now);
                    old = permissions.save(old);
                }
                deprecated.add(old);
            }
        }
        superManager = reconcileSuperManager(superManager, active, turnedSensitive);
        audit.success(
                AuditAction.CATALOG_REGISTERED,
                null,
                service,
                null,
                beforeSnapshot,
                new CatalogSnapshot(keys(active), keys(deprecated)));
        return new Registered(active, deprecated, superManager);
    }

    /**
     * Super Manager holds every non-sensitive CRM permission; a sensitive one only when an admin adds it. So new
     * non-sensitive permissions join it, and ones that just turned sensitive leave it.
     */
    private Role reconcileSuperManager(Role superManager, List<Permission> registered, Set<UUID> turnedSensitive) {
        Set<UUID> heldIds =
                superManager.getPermissions().stream().map(Permission::getId).collect(Collectors.toSet());
        Set<Permission> held = new HashSet<>(superManager.getPermissions());
        boolean changed = held.removeIf(permission -> turnedSensitive.contains(permission.getId()));
        for (Permission permission : registered) {
            if (permission.getRealm() == UserType.INTERNAL
                    && !permission.isSensitive()
                    && !heldIds.contains(permission.getId())) {
                held.add(permission);
                changed = true;
            }
        }
        if (!changed) {
            return superManager;
        }
        superManager.setPermissions(held);
        superManager.setUpdatedAt(Instant.now(clock));
        return roles.update(superManager);
    }

    @Override
    public List<PermissionResponse> list(String service, String realm, Boolean deprecated) {
        UserType realmType = realm == null
                ? null
                : UserType.ofRealm(realm).orElseThrow(() -> new InvalidUserRequestException("Unknown realm " + realm));
        Map<UUID, List<String>> grantedBy = new java.util.HashMap<>();
        for (Role role : roles.findAll()) {
            role.getPermissions()
                    .forEach(permission -> grantedBy
                            .computeIfAbsent(permission.getId(), id -> new ArrayList<>())
                            .add(role.getName()));
        }
        return permissions.findAll().stream()
                .filter(permission -> service == null || permission.getService().equals(service))
                .filter(permission -> realmType == null || permission.getRealm() == realmType)
                .filter(permission -> deprecated == null || permission.isDeprecated() == deprecated)
                .map(permission -> PermissionResponse.builder()
                        .key(permission.getKey())
                        .service(permission.getService())
                        .realm(permission.getRealm().getRealm())
                        .description(permission.getDescription())
                        .sensitive(permission.isSensitive())
                        .deprecated(permission.isDeprecated())
                        .deprecatedAt(permission.getDeprecatedAt())
                        .grantedBy(grantedBy.getOrDefault(permission.getId(), List.of()).stream()
                                .sorted()
                                .toList())
                        .build())
                .toList();
    }

    /** One entry per realm and key; a key listed twice is a mistake in the caller. */
    private static Map<String, Entry> byRealmAndKey(List<Entry> entries) {
        Map<String, Entry> byKey = new java.util.LinkedHashMap<>();
        for (Entry entry : entries) {
            if (byKey.put(entry.realm() + "/" + entry.key(), entry) != null) {
                throw new InvalidUserRequestException(entry.key() + " is listed twice");
            }
        }
        return byKey;
    }

    private static CatalogSnapshot snapshot(List<Permission> catalog) {
        return new CatalogSnapshot(
                keys(catalog.stream()
                        .filter(permission -> !permission.isDeprecated())
                        .toList()),
                keys(catalog.stream().filter(Permission::isDeprecated).toList()));
    }

    private static List<String> keys(List<Permission> catalog) {
        return catalog.stream()
                .map(Permission::getKey)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private record Registered(List<Permission> active, List<Permission> deprecated, Role superManager) {}
}
