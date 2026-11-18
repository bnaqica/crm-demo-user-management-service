package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether an internal user holds Super Manager: the {@code crm-api} client role {@code super-manager}, directly or
 * through a composite. Asks Keycloak and remembers each answer for a short time, as team-scope reads are frequent.
 */
@Component
public class SuperManagerCheck {

    static final String SUPER_MANAGER_ROLE = RoleService.SUPER_MANAGER;

    private final KeycloakClients keycloak;
    private final KeycloakClientIds clientIds;
    private final Clock clock;
    private final Duration ttl;
    private final Map<UUID, Answer> answers = new ConcurrentHashMap<>();

    public SuperManagerCheck(
            KeycloakClients keycloak,
            KeycloakClientIds clientIds,
            Clock clock,
            @Value("${ums.team-scope.super-manager-cache-ttl:1m}") Duration ttl) {
        this.keycloak = keycloak;
        this.clientIds = clientIds;
        this.clock = clock;
        this.ttl = ttl;
    }

    public boolean isSuperManager(UUID sub) {
        Instant now = Instant.now(clock);
        Answer cached = answers.get(sub);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.superManager();
        }
        KeycloakAdminClient client = keycloak.forType(UserType.INTERNAL);
        boolean superManager =
                client.effectiveClientRoles(sub.toString(), clientIds.permissionClientId(UserType.INTERNAL)).stream()
                        .anyMatch(role -> SUPER_MANAGER_ROLE.equals(role.name()));
        answers.put(sub, new Answer(superManager, now.plus(ttl)));
        return superManager;
    }

    private record Answer(boolean superManager, Instant expiresAt) {}
}
