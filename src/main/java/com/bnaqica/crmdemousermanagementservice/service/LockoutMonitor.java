package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.event.KeycloakEvent;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Raises one alert when more than the threshold of distinct accounts in a realm lock within the window, measured
 * in event time. Events older than the window (counted back from the newest event seen) are dropped, including
 * late ones, and duplicates (same id) count once. After an alert, the realm stays quiet for one window.
 * Per instance, in memory.
 */
@Component
class LockoutMonitor {

    private final AlertService alerts;
    private final int threshold;
    private final long windowMillis;
    private final Map<String, RealmWindow> realms = new HashMap<>();

    LockoutMonitor(AlertService alerts, UmsProperties properties) {
        this.alerts = alerts;
        this.threshold = properties.lockoutAlert().threshold();
        this.windowMillis = properties.lockoutAlert().window().toMillis();
    }

    synchronized void record(KeycloakEvent lockout) {
        RealmWindow window = realms.computeIfAbsent(lockout.realm(), realm -> new RealmWindow());
        window.add(lockout);
        long accounts = window.accounts();
        if (accounts > threshold && window.mayAlert()) {
            window.alerted();
            alerts.raise(
                    "mass-lockout",
                    accounts + " accounts locked within " + Duration.ofMillis(windowMillis) + " in realm "
                            + lockout.realm());
        }
    }

    private final class RealmWindow {

        /** Event id to event, for the events inside the window. */
        private final Map<String, KeycloakEvent> events = new HashMap<>();

        private long newest = Long.MIN_VALUE;
        private long lastAlert = Long.MIN_VALUE;

        void add(KeycloakEvent event) {
            newest = Math.max(newest, event.time());
            long start = newest - windowMillis;
            if (event.time() >= start) {
                events.putIfAbsent(event.id(), event);
            }
            events.values().removeIf(old -> old.time() < start);
        }

        long accounts() {
            return events.values().stream()
                    .map(KeycloakEvent::userId)
                    .distinct()
                    .count();
        }

        boolean mayAlert() {
            return lastAlert == Long.MIN_VALUE || newest - lastAlert > windowMillis;
        }

        void alerted() {
            lastAlert = newest;
        }
    }
}
