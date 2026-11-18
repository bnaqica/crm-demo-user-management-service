package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.UserRolesSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.exception.RoleNotFoundException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import com.bnaqica.crmdemousermanagementservice.publisher.UserEventPublisher;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Gives and takes away roles. Keycloak is changed first; the audit event and the user event are then written in one
 * transaction, and Keycloak is put back if that fails. Assignability and approvals are checked by the callers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleAssignments {

    private final RoleService roles;
    private final RoleKeycloakSync keycloakSync;
    private final AuditEventPublisher audit;
    private final UserEventPublisher userEvents;
    private final TransactionOperations transactions;

    /** The UMS roles the user holds. */
    public Set<String> held(UserAccount user) {
        return keycloakSync.heldRoles(user, roles.findAll());
    }

    /** @return the user's roles afterwards; unchanged, with nothing published, if they held it already */
    public List<String> assign(UserAccount user, Role role, String ticketRef) {
        Set<String> before = held(user);
        if (before.contains(role.getName())) {
            return sorted(before);
        }
        keycloakSync.assign(user, role);
        Set<String> after = new HashSet<>(before);
        after.add(role.getName());
        record(AuditAction.ROLE_ASSIGNED, user, role, ticketRef, before, after);
        return sorted(after);
    }

    /** @throws RoleNotFoundException if the user doesn't hold it */
    public List<String> revoke(UserAccount user, Role role, String ticketRef) {
        Set<String> before = held(user);
        if (!before.contains(role.getName())) {
            throw new RoleNotFoundException(role.getName(), user.getSub());
        }
        keycloakSync.revoke(user, role);
        Set<String> after = new HashSet<>(before);
        after.remove(role.getName());
        record(AuditAction.ROLE_REVOKED, user, role, ticketRef, before, after);
        return sorted(after);
    }

    private void record(
            AuditAction action, UserAccount user, Role role, String ticketRef, Set<String> before, Set<String> after) {
        boolean assigned = action == AuditAction.ROLE_ASSIGNED;
        try {
            transactions.executeWithoutResult(status -> {
                audit.success(
                        action,
                        user.getUserType(),
                        user.getSub(),
                        ticketRef,
                        new UserRolesSnapshot(sorted(before)),
                        new UserRolesSnapshot(sorted(after)));
                if (assigned) {
                    userEvents.roleAssigned(user, role.getName());
                } else {
                    userEvents.roleRevoked(user, role.getName());
                }
            });
        } catch (RuntimeException ex) {
            undo(assigned, user, role, action);
            throw ex;
        }
        log.info("{} {} for user {} (ticket {})", action, role.getName(), user.getSub(), ticketRef);
    }

    private void undo(boolean assigned, UserAccount user, Role role, AuditAction action) {
        try {
            if (assigned) {
                keycloakSync.revoke(user, role);
            } else {
                keycloakSync.assign(user, role);
            }
        } catch (RuntimeException restore) {
            log.error(
                    "Couldn't undo {} of {} for user {} in Keycloak; fix it by hand",
                    action,
                    role.getName(),
                    user.getSub(),
                    restore);
        }
    }

    static List<String> sorted(Set<String> names) {
        return names.stream().sorted().toList();
    }
}
