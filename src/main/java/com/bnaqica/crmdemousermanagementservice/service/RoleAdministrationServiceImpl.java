package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.RoleSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.request.AssignRoleRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.CreateRoleRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.UpdateRoleRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.RoleAssignmentResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.RoleResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserRolesResponse;
import com.bnaqica.crmdemousermanagementservice.entity.Permission;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidUserRequestException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Role definitions are committed first and then synced to Keycloak, idempotently, from what was committed, so a
 * Keycloak failure is repaired by saving again. Assignments go through {@link RoleAssignments}; Super Manager is never
 * assigned here: it becomes a grant request for a second admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAdministrationServiceImpl implements RoleAdministrationService {

    private final RoleService roles;
    private final PermissionService permissions;
    private final UserAccountService accounts;
    private final RoleKeycloakSync keycloakSync;
    private final RoleAssignments assignments;
    private final AdminAccounts admins;
    private final PrivilegedGrantService grants;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Override
    public List<RoleResponse> list() {
        return roles.findAll().stream().map(RoleMapper::toResponse).toList();
    }

    @Override
    public RoleResponse get(String name) {
        return RoleMapper.toResponse(roles.get(name));
    }

    @Override
    public RoleResponse create(CreateRoleRequest request) {
        return audit.auditedCreate(
                AuditAction.ROLE_CREATED, request.assignableTo(), request, request.ticketRef(), () -> {
                    Instant now = Instant.now(clock);
                    Role saved = transactions.execute(status -> {
                        Role created = roles.create(Role.builder()
                                .id(UUID.randomUUID())
                                .name(request.name())
                                .description(request.description())
                                .owner(request.owner())
                                .assignableTo(request.assignableTo())
                                .permissions(grantable(request.assignableTo(), request.permissions()))
                                .createdAt(now)
                                .updatedAt(now)
                                .build());
                        audit.success(
                                AuditAction.ROLE_CREATED,
                                created.getAssignableTo().getRealm(),
                                created.getName(),
                                request.ticketRef(),
                                null,
                                snapshot(created));
                        return created;
                    });
                    keycloakSync.syncRole(saved, RoleRules.keys(saved));
                    log.info("Created role {} (ticket {})", saved.getName(), request.ticketRef());
                    return RoleMapper.toResponse(saved);
                });
    }

    @Override
    public RoleResponse update(String name, UpdateRoleRequest request) {
        Role current = roles.get(name);
        return audit.audited(AuditAction.ROLE_UPDATED, current.getAssignableTo(), name, request.ticketRef(), () -> {
            Role saved = transactions.execute(status -> {
                Role role = roles.lockForUpdate(name);
                RoleSnapshot before = snapshot(role);
                Set<Permission> granted = grantable(role.getAssignableTo(), request.permissions());
                if (RoleService.SUPER_MANAGER.equals(role.getName())) {
                    checkSuperManagerKeepsEveryNonSensitivePermission(granted);
                }
                role.setPermissions(granted);
                role.setDescription(request.description());
                role.setOwner(request.owner());
                role.setUpdatedAt(Instant.now(clock));
                Role updated = roles.update(role);
                audit.success(
                        AuditAction.ROLE_UPDATED,
                        updated.getAssignableTo().getRealm(),
                        name,
                        request.ticketRef(),
                        before,
                        snapshot(updated));
                return updated;
            });
            // After the commit, from what was committed; idempotent, so a retry repairs a Keycloak failure
            keycloakSync.syncRole(saved, RoleRules.keys(saved));
            log.info("Updated role {} (ticket {})", name, request.ticketRef());
            return RoleMapper.toResponse(saved);
        });
    }

    /** Admins may add or remove sensitive permissions of Super Manager, never the non-sensitive CRM ones. */
    private void checkSuperManagerKeepsEveryNonSensitivePermission(Set<Permission> granted) {
        Set<String> keys = granted.stream().map(Permission::getKey).collect(Collectors.toSet());
        permissions.findAll().stream()
                .filter(permission -> permission.getRealm() == UserType.INTERNAL
                        && !permission.isSensitive()
                        && !permission.isDeprecated())
                .map(Permission::getKey)
                .filter(key -> !keys.contains(key))
                .findFirst()
                .ifPresent(key -> {
                    throw new InvalidUserRequestException(
                            "Super Manager holds every non-sensitive CRM permission; " + key + " can't be removed");
                });
    }

    @Override
    public UserRolesResponse rolesOf(UUID sub) {
        UserAccount user = accounts.get(sub);
        return new UserRolesResponse(sub, RoleAssignments.sorted(assignments.held(user)));
    }

    @Override
    public RoleAssignmentResponse assign(UUID sub, AssignRoleRequest request) {
        return audit.audited(AuditAction.ROLE_ASSIGNED, sub.toString(), request.ticketRef(), () -> {
            UserAccount user = accounts.find(sub).orElseThrow(() -> admins.notAUser(sub));
            Role role = roles.get(request.role());
            if (role.getAssignableTo() != user.getUserType()) {
                throw new InvalidUserRequestException(
                        "Role " + role.getName() + " is for " + role.getAssignableTo() + " users only");
            }
            if (RoleService.SUPER_MANAGER.equals(role.getName())
                    && !assignments.held(user).contains(role.getName())) {
                return RoleAssignmentResponse.pending(grants.requestSuperManager(user, role, request.ticketRef()));
            }
            return RoleAssignmentResponse.applied(
                    new UserRolesResponse(sub, assignments.assign(user, role, request.ticketRef())));
        });
    }

    @Override
    public UserRolesResponse revoke(UUID sub, String roleName, String ticketRef) {
        return audit.audited(AuditAction.ROLE_REVOKED, sub.toString(), ticketRef, () -> {
            UserAccount user = accounts.get(sub);
            return new UserRolesResponse(sub, assignments.revoke(user, roles.get(roleName), ticketRef));
        });
    }

    /**
     * The permissions to store for {@code keys}: each must be in the catalog for the role's realm and not deprecated,
     * and a {@code <resource>:write} brings {@code <resource>:read}.
     */
    private Set<Permission> grantable(UserType realm, List<String> keys) {
        Set<String> wanted = new HashSet<>(keys);
        keys.stream()
                .filter(key -> key.endsWith(":write"))
                .map(key -> key.substring(0, key.length() - ":write".length()) + ":read")
                .forEach(wanted::add);
        List<Permission> found = permissions.findAll(realm, wanted);
        Set<String> foundKeys = found.stream().map(Permission::getKey).collect(Collectors.toSet());
        for (String key : wanted) {
            if (!foundKeys.contains(key)) {
                UserType other = realm == UserType.INTERNAL ? UserType.EXTERNAL : UserType.INTERNAL;
                throw new InvalidUserRequestException(
                        permissions.find(other, key).isPresent()
                                ? key + " belongs to " + other.getRealm() + "; a role holds one realm's permissions"
                                : key + " isn't in the " + realm.getRealm() + " catalog");
            }
        }
        found.stream().filter(Permission::isDeprecated).findFirst().ifPresent(permission -> {
            throw new InvalidUserRequestException(permission.getKey() + " is deprecated and can't be granted");
        });
        return new HashSet<>(found);
    }

    private static RoleSnapshot snapshot(Role role) {
        return RoleSnapshot.of(role, sorted(RoleRules.keys(role)));
    }

    private static List<String> sorted(Set<String> names) {
        List<String> list = new ArrayList<>(names);
        list.sort(null);
        return list;
    }
}
