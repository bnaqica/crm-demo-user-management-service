package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import com.bnaqica.crmdemousermanagementservice.dto.event.KeycloakEvent;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.EmailAlreadyInUseException;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import com.bnaqica.crmdemousermanagementservice.publisher.UserEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Records a self-registered portal user as an EXTERNAL STARTER and makes sure they hold {@code external-starter}.
 * A sub already recorded is skipped, so a redelivered event changes nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StarterRegistrations {

    private final UserAccountService accounts;
    private final KeycloakClients keycloak;
    private final RoleService roles;
    private final RoleKeycloakSync keycloakSync;
    private final UserEventPublisher userEvents;
    private final AlertService alerts;
    private final TransactionOperations transactions;
    private final Clock clock;

    public void record(KeycloakEvent event, UUID sub) {
        if (accounts.find(sub).isPresent()) {
            log.debug("Registration {} of user {} is recorded already", event.id(), sub);
            return;
        }
        KeycloakUser registered;
        try {
            registered = keycloak.forType(UserType.EXTERNAL).getUser(sub.toString());
        } catch (KeycloakClientException ex) {
            if (ex.isNotFound()) {
                log.info("Registered user {} no longer exists in portal; nothing to record", sub);
                return;
            }
            throw ex;
        }
        Instant now = Instant.now(clock);
        UserAccount account = UserAccount.builder()
                .sub(sub)
                .userType(UserType.EXTERNAL)
                .email(EmailRules.normalise(registered.email()))
                .firstName(registered.firstName())
                .lastName(registered.lastName())
                .status(UserStatus.STARTER)
                .createdAt(event.time() > 0 ? Instant.ofEpochMilli(event.time()) : now) // starts the expiry clock
                .updatedAt(now)
                .build();
        Role starter = roles.get(UserPromotionServiceImpl.STARTER);
        if (!keycloakSync.heldRoles(account, List.of(starter)).contains(starter.getName())) {
            keycloakSync.assign(account, starter);
        }
        try {
            transactions.executeWithoutResult(status -> userEvents.userCreated(accounts.create(account)));
        } catch (EmailAlreadyInUseException ex) {
            if (accounts.find(sub).isPresent()) {
                log.debug("Registration {} of user {} was recorded at the same time", event.id(), sub);
                return;
            }
            alerts.raise(
                    "registration-email-clash",
                    "Self-registered portal user " + sub + " uses an email another account holds; not recorded");
            return;
        }
        log.info("Recorded self-registered user {} as STARTER (event {})", sub, event.id());
    }
}
