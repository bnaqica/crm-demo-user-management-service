package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.event.KeycloakEvent;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Raises an alert and writes an audit record whenever the break-glass admin signs in. A redelivered event is
 * dropped by its id here, and its audit event id is derived from it, so audit consumers drop it too.
 */
@Component
public class BreakGlassMonitor {

    private final AlertService alerts;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;
    private final String breakGlassId;
    /** Ids of the last events handled, to drop Kafka redeliveries. */
    private final Set<String> recentEvents =
            Collections.newSetFromMap(Collections.synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > RECENT_EVENTS;
                }
            }));

    private static final int RECENT_EVENTS = 1000;

    public BreakGlassMonitor(
            AlertService alerts,
            AuditEventPublisher audit,
            TransactionOperations transactions,
            @Value("${ums.break-glass-admin-id:}") String breakGlassId) {
        this.alerts = alerts;
        this.audit = audit;
        this.transactions = transactions;
        this.breakGlassId = breakGlassId;
    }

    /** Call for every LOGIN event in crm-admin. */
    public void login(KeycloakEvent event) {
        if (breakGlassId.isBlank()
                || !AdminAccounts.REALM.equals(event.realm())
                || !breakGlassId.equals(event.userId())) {
            return;
        }
        if (!recentEvents.add(event.id())) {
            return; // Kafka redelivered it; alerted already
        }
        alerts.raise(
                "break-glass-login", "The break-glass admin " + breakGlassId + " signed in (event " + event.id() + ")");
        transactions.executeWithoutResult(status -> audit.system(
                AuditAction.BREAK_GLASS_LOGIN,
                AdminAccounts.REALM,
                breakGlassId,
                Map.of("keycloakEventId", event.id(), "time", Instant.ofEpochMilli(event.time())),
                UUID.nameUUIDFromBytes(("break-glass-login:" + event.id()).getBytes(StandardCharsets.UTF_8))));
    }
}
