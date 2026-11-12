package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakRole;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import com.bnaqica.crmdemousermanagementservice.dto.UserSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.request.CreateInternalUserRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.InviteExternalUserRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserResponse;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.EmailAlreadyInUseException;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidUserRequestException;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import com.bnaqica.crmdemousermanagementservice.publisher.UserEventPublisher;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Creates the Keycloak user first, then sends the invite and stores the UMS record. If anything after the Keycloak
 * create fails, the Keycloak user is deleted again so no half-created account is left behind.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserOnboardingServiceImpl implements UserOnboardingService {

    static final List<String> INVITE_ACTIONS = List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP");

    private final KeycloakClients keycloak;
    private final UserAccountService accounts;
    private final UmsProperties properties;
    private final EmailRules emailRules;
    private final UserEventPublisher userEvents;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Override
    public UserResponse createInternal(CreateInternalUserRequest request) {
        return audit.auditedCreate(
                AuditAction.USER_CREATED,
                UserType.INTERNAL,
                requested(UserType.INTERNAL, request.email(), request.firstName(), request.lastName()),
                request.ticketRef(),
                () -> doCreateInternal(request));
    }

    @Override
    public UserResponse inviteExternal(InviteExternalUserRequest request) {
        return audit.auditedCreate(
                AuditAction.USER_INVITED,
                UserType.EXTERNAL,
                requested(UserType.EXTERNAL, request.email(), request.firstName(), request.lastName()),
                request.ticketRef(),
                () -> doInviteExternal(request));
    }

    private UserResponse doCreateInternal(CreateInternalUserRequest request) {
        NewUser user = new NewUser(
                UserType.INTERNAL,
                EmailRules.normalise(request.email()),
                request.firstName(),
                request.lastName(),
                request.ticketRef());
        emailRules.checkDomain(user.type(), user.email());
        return onboard(user, null, AuditAction.USER_CREATED);
    }

    private UserResponse doInviteExternal(InviteExternalUserRequest request) {
        NewUser user = new NewUser(
                UserType.EXTERNAL,
                EmailRules.normalise(request.email()),
                request.firstName(),
                request.lastName(),
                request.ticketRef());
        emailRules.checkDomain(user.type(), user.email());
        if (!properties.externalRoles().contains(request.role())) {
            throw new InvalidUserRequestException("Role " + request.role() + " can't be given to an external user");
        }
        return onboard(user, request.role(), AuditAction.USER_INVITED);
    }

    private UserResponse onboard(NewUser user, String role, AuditAction action) {
        emailRules.checkFree(user.email());
        KeycloakAdminClient client = keycloak.forType(user.type());
        String id = createInKeycloak(client, user);
        try {
            if (role != null) {
                client.addRealmRoles(id, List.of(availableRole(client, id, role)));
            }
            client.executeActionsEmail(id, (int) properties.inviteLifespan().toSeconds(), INVITE_ACTIONS);
            Instant now = Instant.now(clock);
            UserAccount account = UserAccount.builder()
                    .sub(UUID.fromString(id))
                    .userType(user.type())
                    .email(user.email())
                    .firstName(user.firstName())
                    .lastName(user.lastName())
                    .status(UserStatus.INVITED)
                    .createdTicket(user.ticketRef())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            UserAccount saved = transactions.execute(status -> {
                UserAccount created = accounts.create(account);
                userEvents.userCreated(created);
                audit.success(
                        action,
                        created.getUserType(),
                        created.getSub(),
                        user.ticketRef(),
                        null,
                        UserSnapshot.of(created));
                return created;
            });
            log.info("Invited {} user {} (ticket {})", user.type(), id, user.ticketRef());
            return UserMapper.toResponse(saved);
        } catch (RuntimeException ex) {
            deleteQuietly(client, id);
            throw ex;
        }
    }

    private String createInKeycloak(KeycloakAdminClient client, NewUser user) {
        ResponseEntity<Void> response;
        try {
            response = client.create(KeycloakUser.builder()
                    .username(user.email())
                    .email(user.email())
                    .firstName(user.firstName())
                    .lastName(user.lastName())
                    .enabled(true)
                    .emailVerified(false)
                    .requiredActions(INVITE_ACTIONS)
                    .build());
        } catch (KeycloakClientException ex) {
            if (ex.isConflict()) {
                throw new EmailAlreadyInUseException();
            }
            throw ex;
        }
        URI location = response.getHeaders().getLocation();
        if (location == null) {
            // Created, but its id is unknown: find it by email so it can be removed again
            client.findByEmail(user.email(), true).forEach(created -> deleteQuietly(client, created.id()));
            throw new KeycloakClientException(
                    response.getStatusCode().value(), "Keycloak created a user without a Location");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** What the admin asked for, recorded on a failed create. */
    private static UserSnapshot requested(UserType type, String email, String firstName, String lastName) {
        return new UserSnapshot(type, email == null ? null : EmailRules.normalise(email), firstName, lastName, null);
    }

    private static KeycloakRole availableRole(KeycloakAdminClient client, String id, String role) {
        return client.availableRealmRoles(id).stream()
                .filter(candidate -> candidate.name().equals(role))
                .findFirst()
                .orElseThrow(() -> new InvalidUserRequestException("Role " + role + " doesn't exist in the realm"));
    }

    private static void deleteQuietly(KeycloakAdminClient client, String id) {
        try {
            client.deleteUser(id);
        } catch (RuntimeException ex) {
            log.error("Couldn't remove half-created Keycloak user {}; delete it by hand", id, ex);
        }
    }

    private record NewUser(UserType type, String email, String firstName, String lastName, String ticketRef) {}
}
