package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.event.KeycloakEvent;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidKeycloakEventException;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A first {@code LOGIN} moves an INVITED user to ACTIVE; a crm-admin {@code LOGIN} goes to the break-glass check;
 * a portal {@code REGISTER} records a STARTER; lockout events feed the mass-lockout alert. Everything else is ignored here. Handling is idempotent, because Kafka delivers at least once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakEventServiceImpl implements KeycloakEventService {

    private static final Set<String> LOCKOUT_TYPES =
            Set.of("USER_DISABLED_BY_TEMPORARY_LOCKOUT", "USER_DISABLED_BY_PERMANENT_LOCKOUT");

    private final JsonMapper jsonMapper;
    private final UserAccountService accounts;
    private final LockoutMonitor lockoutMonitor;
    private final BreakGlassMonitor breakGlass;
    private final StarterRegistrations starters;
    private final Clock clock;

    @Override
    public void handle(String payload) {
        KeycloakEvent event = parse(payload);
        if ("LOGIN".equals(event.type()) && AdminAccounts.REALM.equals(event.realm())) {
            breakGlass.login(event);
        } else if ("LOGIN".equals(event.type())) {
            activateIfInvited(event);
        } else if ("REGISTER".equals(event.type())
                && UserType.EXTERNAL.getRealm().equals(event.realm())) {
            UUID sub = parseSub(event.userId());
            if (sub != null) {
                starters.record(event, sub);
            }
        } else if (LOCKOUT_TYPES.contains(event.type())) {
            lockoutMonitor.record(event);
        } else {
            log.debug("Ignored Keycloak event {} of type {}", event.id(), event.type());
        }
    }

    private void activateIfInvited(KeycloakEvent event) {
        UUID sub = parseSub(event.userId());
        if (sub == null) {
            return;
        }
        accounts.find(sub)
                .filter(account -> account.getStatus() == UserStatus.INVITED)
                .ifPresent(account -> {
                    account.setStatus(UserStatus.ACTIVE);
                    account.setUpdatedAt(Instant.now(clock));
                    accounts.update(account);
                    log.info("User {} is ACTIVE after first login (event {})", sub, event.id());
                });
    }

    private KeycloakEvent parse(String payload) {
        try {
            KeycloakEvent event = jsonMapper.readValue(payload, KeycloakEvent.class);
            if (event.id() == null || event.type() == null) {
                throw new InvalidKeycloakEventException("Keycloak event without id or type", null);
            }
            return event;
        } catch (JacksonException ex) {
            throw new InvalidKeycloakEventException("Keycloak event isn't valid JSON", ex);
        }
    }

    private static UUID parseSub(String userId) {
        try {
            return userId == null ? null : UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            return null; // events for unknown usernames carry no user id
        }
    }
}
