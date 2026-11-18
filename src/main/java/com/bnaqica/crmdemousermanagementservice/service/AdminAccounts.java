package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakRole;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.exception.EmailAlreadyInUseException;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidUserRequestException;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import com.bnaqica.crmdemousermanagementservice.exception.LastAdminException;
import com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/** Admin accounts in crm-admin, through the {@code ums-admin} service account. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccounts {

    static final String REALM = "crm-admin";
    static final String ADMIN_ROLE = "admin";
    private static final int MAX_ADMINS = 1000;

    private final KeycloakClients keycloak;
    private final UmsProperties properties;
    private final EmailRules emailRules;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;

    /** Fails if the email is taken in any realm: an admin account is never also a CRM or portal account. */
    public void checkEmailFree(String email) {
        emailRules.checkFree(email);
        if (!client().findByEmail(email, true).isEmpty()) {
            throw new EmailAlreadyInUseException();
        }
    }

    /** Creates the admin with the realm role {@code admin} and emails them a link to set a password and TOTP. */
    public String create(String email, String firstName, String lastName, String ticketRef) {
        checkEmailFree(email);
        KeycloakAdminClient client = client();
        URI location = client.create(KeycloakUser.builder()
                        .username(email)
                        .email(email)
                        .firstName(firstName)
                        .lastName(lastName)
                        .enabled(true)
                        .emailVerified(false)
                        .requiredActions(UserOnboardingServiceImpl.INVITE_ACTIONS)
                        .build())
                .getHeaders()
                .getLocation();
        String id = location.getPath().substring(location.getPath().lastIndexOf('/') + 1);
        try {
            KeycloakRole admin = client.availableRealmRoles(id).stream()
                    .filter(role -> role.name().equals(ADMIN_ROLE))
                    .findFirst()
                    .orElseThrow(() -> new KeycloakClientException(404, "crm-admin has no admin role"));
            client.addRealmRoles(id, List.of(admin));
            client.executeActionsEmail(
                    id, (int) properties.inviteLifespan().toSeconds(), UserOnboardingServiceImpl.INVITE_ACTIONS);
        } catch (RuntimeException ex) {
            removeHalfCreated(client, id);
            throw ex;
        }
        transactions.executeWithoutResult(
                status -> audit.success(AuditAction.ADMIN_CREATED, REALM, id, ticketRef, null, Map.of("email", email)));
        log.info("Created admin account {} (ticket {})", id, ticketRef);
        return id;
    }

    /**
     * The exception for a sub that has no UMS user record: 422 if it is an admin account (admin accounts never get
     * CRM roles, portal roles or teams), 404 otherwise.
     */
    public RuntimeException notAUser(UUID sub) {
        return isAdminAccount(sub)
                ? new InvalidUserRequestException("Admin accounts can't hold CRM or portal roles or be in teams")
                : new UserNotFoundException(sub);
    }

    /** Disables an admin account and ends its sessions, unless it is the last enabled admin. */
    public synchronized void disable(UUID id, String ticketRef) {
        String adminId = existingAdmin(id);
        checkNotLastAdmin(adminId);
        client().updateUser(adminId, KeycloakUser.builder().enabled(false).build());
        client().logout(adminId);
        transactions.executeWithoutResult(status -> audit.success(
                AuditAction.ADMIN_DISABLED,
                REALM,
                adminId,
                ticketRef,
                Map.of("enabled", true),
                Map.of("enabled", false)));
        log.info("Disabled admin account {} (ticket {})", adminId, ticketRef);
    }

    /** Deletes an admin account, unless it is the last enabled admin. */
    public synchronized void delete(UUID id, String ticketRef) {
        String adminId = existingAdmin(id);
        checkNotLastAdmin(adminId);
        client().deleteUser(adminId);
        transactions.executeWithoutResult(status ->
                audit.success(AuditAction.ADMIN_DELETED, REALM, adminId, ticketRef, Map.of("exists", true), null));
        log.info("Deleted admin account {} (ticket {})", adminId, ticketRef);
    }

    private String existingAdmin(UUID id) {
        if (!isAdminAccount(id)) {
            throw new UserNotFoundException(id);
        }
        return id.toString();
    }

    /**
     * Refuses when {@code adminId} is enabled and no other enabled account holds {@code admin}. Synchronised callers
     * serialise this within one instance; two instances could still race (see the change's design).
     */
    private void checkNotLastAdmin(String adminId) {
        KeycloakAdminClient client = client();
        List<String> enabledAdmins = client.findByEnabled(true, true, MAX_ADMINS).stream()
                .map(KeycloakUser::id)
                .filter(this::holdsAdmin)
                .toList();
        if (enabledAdmins.contains(adminId) && enabledAdmins.size() <= 1) {
            throw new LastAdminException();
        }
    }

    /** Reading realm role mappings needs view-realm; the admin role not being available to add means it is held. */
    private boolean holdsAdmin(String userId) {
        return client().availableRealmRoles(userId).stream()
                .noneMatch(role -> role.name().equals(ADMIN_ROLE));
    }

    /** Deletes an account whose setup failed, so the approval can be retried. */
    private static void removeHalfCreated(KeycloakAdminClient client, String id) {
        try {
            client.deleteUser(id);
        } catch (RuntimeException ex) {
            log.error("Couldn't delete half-created admin account {}; delete it by hand", id, ex);
        }
    }

    /** Whether {@code sub} is an account in crm-admin. */
    public boolean isAdminAccount(UUID sub) {
        try {
            client().getUser(sub.toString());
            return true;
        } catch (KeycloakClientException ex) {
            if (ex.isNotFound()) {
                return false;
            }
            throw ex;
        }
    }

    private KeycloakAdminClient client() {
        return keycloak.adminRealm();
    }
}
