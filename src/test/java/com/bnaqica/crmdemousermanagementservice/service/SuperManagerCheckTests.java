package com.bnaqica.crmdemousermanagementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakClient;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakRole;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuperManagerCheckTests {

    private static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");
    private static final UUID SUB = UUID.randomUUID();

    private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    private Instant now = T0;
    private SuperManagerCheck check;

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
        when(keycloak.clientsByClientId("crm-api")).thenReturn(List.of(new KeycloakClient("uuid-1", "crm-api")));
        var clients = new KeycloakClients(Map.of(UserType.INTERNAL, keycloak));
        check = new SuperManagerCheck(clients, new KeycloakClientIds(clients), clock, Duration.ofMinutes(1));
    }

    @Test
    void holderOfTheRoleIsASuperManager() {
        when(keycloak.effectiveClientRoles(SUB.toString(), "uuid-1"))
                .thenReturn(List.of(new KeycloakRole("r", "super-manager")));

        assertThat(check.isSuperManager(SUB)).isTrue();
    }

    @Test
    void otherRolesDontCount() {
        when(keycloak.effectiveClientRoles(SUB.toString(), "uuid-1"))
                .thenReturn(List.of(new KeycloakRole("r", "team-manager")));

        assertThat(check.isSuperManager(SUB)).isFalse();
    }

    @Test
    void answerIsReusedWithinTheTtl() {
        when(keycloak.effectiveClientRoles(anyString(), anyString())).thenReturn(List.of());

        check.isSuperManager(SUB);
        now = T0.plusSeconds(59);
        check.isSuperManager(SUB);

        verify(keycloak, times(1)).effectiveClientRoles(SUB.toString(), "uuid-1");
    }

    @Test
    void keycloakIsAskedAgainAfterTheTtl() {
        when(keycloak.effectiveClientRoles(anyString(), anyString())).thenReturn(List.of());

        check.isSuperManager(SUB);
        now = T0.plusSeconds(61);
        check.isSuperManager(SUB);

        verify(keycloak, times(2)).effectiveClientRoles(SUB.toString(), "uuid-1");
    }
}
