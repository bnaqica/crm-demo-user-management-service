package com.bnaqica.crmdemousermanagementservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.event.KeycloakEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** user-accounts spec, "Mass lockout alert", with a threshold of 5 accounts in 5 minutes. */
class LockoutMonitorTests {

    private static final Instant T0 = Instant.parse("2026-11-11T10:00:00Z");

    private final List<String> alerts = new ArrayList<>();
    private final LockoutMonitor monitor = new LockoutMonitor(
            (type, message) -> alerts.add(type + ": " + message),
            new UmsProperties(null, null, null, null, null, new UmsProperties.LockoutAlert(5, Duration.ofMinutes(5))));

    private static KeycloakEvent lockout(String realm, String user, Instant at) {
        return new KeycloakEvent(
                UUID.randomUUID().toString(),
                at.toEpochMilli(),
                realm,
                "USER_DISABLED_BY_TEMPORARY_LOCKOUT",
                user,
                null,
                Map.of());
    }

    private void lockAccounts(int count, String realm, Instant start, Duration apart) {
        for (int i = 0; i < count; i++) {
            monitor.record(lockout(realm, "user-" + realm + "-" + i, start.plus(apart.multipliedBy(i))));
        }
    }

    @Test
    void tenAccountsInFiveMinutesRaiseOneAlert() {
        lockAccounts(10, "crm-internal", T0, Duration.ofSeconds(20));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst()).startsWith("mass-lockout").contains("crm-internal");
    }

    @Test
    void thresholdItselfDoesNotAlert() {
        lockAccounts(5, "crm-internal", T0, Duration.ofSeconds(20));

        assertThat(alerts).isEmpty();
    }

    @Test
    void lockoutsSpreadBeyondTheWindowDoNotAlert() {
        lockAccounts(10, "crm-internal", T0, Duration.ofMinutes(2));

        assertThat(alerts).isEmpty();
    }

    @Test
    void sameAccountLockingRepeatedlyCountsOnce() {
        for (int i = 0; i < 10; i++) {
            monitor.record(lockout("crm-internal", "same-user", T0.plusSeconds(i)));
        }

        assertThat(alerts).isEmpty();
    }

    @Test
    void duplicateEventsCountOnce() {
        KeycloakEvent event = lockout("crm-internal", "u1", T0);
        for (int i = 0; i < 10; i++) {
            monitor.record(event);
        }

        assertThat(alerts).isEmpty();
    }

    @Test
    void lateEventOlderThanTheWindowIsNotCounted() {
        lockAccounts(5, "crm-internal", T0.plus(Duration.ofMinutes(30)), Duration.ofSeconds(1));
        monitor.record(lockout("crm-internal", "late-user", T0));

        assertThat(alerts).isEmpty();
    }

    @Test
    void realmsAreCountedSeparately() {
        lockAccounts(3, "crm-internal", T0, Duration.ofSeconds(1));
        lockAccounts(3, "portal", T0, Duration.ofSeconds(1));

        assertThat(alerts).isEmpty();
    }

    @Test
    void noSecondAlertWithinTheSameWindow() {
        lockAccounts(12, "crm-internal", T0, Duration.ofSeconds(1));

        assertThat(alerts).hasSize(1);
    }
}
