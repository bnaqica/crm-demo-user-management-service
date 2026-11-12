package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import com.bnaqica.crmdemousermanagementservice.dto.UserSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.request.UpdateUserRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserResponse;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.exception.EmailAlreadyInUseException;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidStatusTransitionException;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import com.bnaqica.crmdemousermanagementservice.publisher.UserEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Keycloak is changed first; the UMS record, the user event and the success audit are then written in one
 * transaction. Any failure is audited as such.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSupportServiceImpl implements UserSupportService {

    private static final Set<String> MFA_CREDENTIAL_TYPES = Set.of("otp", "webauthn", "webauthn-passwordless");

    private final KeycloakClients keycloak;
    private final UserAccountService accounts;
    private final EmailRules emailRules;
    private final UserEventPublisher userEvents;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Override
    public UserResponse disable(UUID sub, String ticketRef) {
        return audit.audited(AuditAction.USER_DISABLED, sub.toString(), ticketRef, () -> {
            UserAccount account = withStatusChange(sub, UserStatus.DISABLED);
            UserSnapshot before = UserSnapshot.of(account);
            KeycloakAdminClient client = client(account);
            client.updateUser(
                    sub.toString(), KeycloakUser.builder().enabled(false).build());
            client.logout(sub.toString());
            UserAccount saved = transactions.execute(status -> {
                UserAccount disabled = saveStatus(account, UserStatus.DISABLED);
                userEvents.userDisabled(disabled);
                auditSuccess(AuditAction.USER_DISABLED, disabled, ticketRef, before);
                return disabled;
            });
            log.info("Disabled user {} (ticket {})", sub, ticketRef);
            return UserMapper.toResponse(saved);
        });
    }

    @Override
    public UserResponse enable(UUID sub, String ticketRef) {
        return audit.audited(AuditAction.USER_ENABLED, sub.toString(), ticketRef, () -> {
            UserAccount account = withStatusChange(sub, UserStatus.ACTIVE);
            UserSnapshot before = UserSnapshot.of(account);
            client(account)
                    .updateUser(
                            sub.toString(), KeycloakUser.builder().enabled(true).build());
            UserAccount saved = transactions.execute(status -> {
                UserAccount enabled = saveStatus(account, UserStatus.ACTIVE);
                auditSuccess(AuditAction.USER_ENABLED, enabled, ticketRef, before);
                return enabled;
            });
            log.info("Enabled user {} (ticket {})", sub, ticketRef);
            return UserMapper.toResponse(saved);
        });
    }

    @Override
    public void unlock(UUID sub, String ticketRef) {
        keycloakAction(AuditAction.USER_UNLOCKED, sub, ticketRef, (client, id) -> client.clearBruteForce(id));
    }

    @Override
    public void logout(UUID sub, String ticketRef) {
        keycloakAction(AuditAction.USER_LOGGED_OUT, sub, ticketRef, (client, id) -> client.logout(id));
    }

    @Override
    public void resetMfa(UUID sub, String ticketRef) {
        keycloakAction(AuditAction.USER_MFA_RESET, sub, ticketRef, (client, id) -> {
            client.credentials(id).stream()
                    .filter(credential -> MFA_CREDENTIAL_TYPES.contains(credential.type()))
                    .forEach(credential -> client.deleteCredential(id, credential.id()));
            client.updateUser(
                    id,
                    KeycloakUser.builder()
                            .requiredActions(withAction(client.getUser(id).requiredActions(), "CONFIGURE_TOTP"))
                            .build());
        });
    }

    @Override
    public UserResponse update(UUID sub, UpdateUserRequest request) {
        return audit.audited(AuditAction.USER_UPDATED, sub.toString(), request.ticketRef(), () -> {
            UserAccount account = accounts.get(sub);
            UserSnapshot before = UserSnapshot.of(account);
            String email = EmailRules.normalise(request.email());
            KeycloakAdminClient client = client(account);
            KeycloakUser previous = client.getUser(sub.toString());
            KeycloakUser.KeycloakUserBuilder change =
                    KeycloakUser.builder().firstName(request.firstName()).lastName(request.lastName());
            if (!email.equals(account.getEmail())) {
                emailRules.checkDomain(account.getUserType(), email);
                emailRules.checkFree(email, sub);
                change.email(email)
                        .emailVerified(false)
                        .requiredActions(withAction(previous.requiredActions(), "VERIFY_EMAIL"));
            }
            updateKeycloak(client, sub, change.build());
            account.setEmail(email);
            account.setFirstName(request.firstName());
            account.setLastName(request.lastName());
            account.setUpdatedAt(Instant.now(clock));
            UserAccount saved;
            try {
                saved = transactions.execute(status -> {
                    UserAccount updated = accounts.update(account);
                    auditSuccess(AuditAction.USER_UPDATED, updated, request.ticketRef(), before);
                    return updated;
                });
            } catch (RuntimeException ex) {
                restoreKeycloak(client, sub, previous);
                throw ex;
            }
            log.info("Updated user {} (ticket {})", sub, request.ticketRef());
            return UserMapper.toResponse(saved);
        });
    }

    /** Keycloak refuses an email another account in the same realm holds; that is the same clash as ours. */
    private static void updateKeycloak(KeycloakAdminClient client, UUID sub, KeycloakUser change) {
        try {
            client.updateUser(sub.toString(), change);
        } catch (KeycloakClientException ex) {
            if (ex.isConflict()) {
                throw new EmailAlreadyInUseException();
            }
            throw ex;
        }
    }

    /** Puts Keycloak back as it was when the UMS couldn't save the change, so the two don't drift. */
    private static void restoreKeycloak(KeycloakAdminClient client, UUID sub, KeycloakUser previous) {
        try {
            client.updateUser(
                    sub.toString(),
                    KeycloakUser.builder()
                            .email(previous.email())
                            .firstName(previous.firstName())
                            .lastName(previous.lastName())
                            .emailVerified(previous.emailVerified())
                            .requiredActions(
                                    previous.requiredActions() == null ? List.of() : previous.requiredActions())
                            .build());
        } catch (RuntimeException ex) {
            log.error("Couldn't restore Keycloak profile of user {} after a failed update; fix it by hand", sub, ex);
        }
    }

    /** An action that changes only Keycloak, not the UMS record. */
    private void keycloakAction(AuditAction action, UUID sub, String ticketRef, KeycloakCall call) {
        audit.audited(action, sub.toString(), ticketRef, () -> {
            UserAccount account = accounts.get(sub);
            call.run(client(account), sub.toString());
            transactions.executeWithoutResult(
                    status -> audit.success(action, account.getUserType(), sub, ticketRef, null, null));
            log.info("{} for user {} (ticket {})", action, sub, ticketRef);
            return null;
        });
    }

    private void auditSuccess(AuditAction action, UserAccount after, String ticketRef, UserSnapshot before) {
        audit.success(action, after.getUserType(), after.getSub(), ticketRef, before, UserSnapshot.of(after));
    }

    private UserAccount withStatusChange(UUID sub, UserStatus next) {
        UserAccount account = accounts.get(sub);
        if (!account.getStatus().canBecome(next)) {
            throw new InvalidStatusTransitionException(account.getStatus(), next);
        }
        return account;
    }

    private UserAccount saveStatus(UserAccount account, UserStatus status) {
        account.setStatus(status);
        account.setUpdatedAt(Instant.now(clock));
        return accounts.update(account);
    }

    private KeycloakAdminClient client(UserAccount account) {
        return keycloak.forType(account.getUserType());
    }

    private static List<String> withAction(List<String> current, String action) {
        List<String> actions = new ArrayList<>(current == null ? List.of() : current);
        if (!actions.contains(action)) {
            actions.add(action);
        }
        return actions;
    }

    @FunctionalInterface
    private interface KeycloakCall {
        void run(KeycloakAdminClient client, String id);
    }
}
