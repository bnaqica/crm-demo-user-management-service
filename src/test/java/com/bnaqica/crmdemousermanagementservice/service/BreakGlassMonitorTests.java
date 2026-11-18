package com.bnaqica.crmdemousermanagementservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.event.KeycloakEvent;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** admin-access spec, "Break-glass alert". */
class BreakGlassMonitorTests {

    private static final String BREAK_GLASS = "0b4f9a1e-0000-4000-8000-00000000b6a5";

    private final AlertService alerts = mock(AlertService.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);

    private BreakGlassMonitor monitor(String configured) {
        return new BreakGlassMonitor(alerts, audit, TransactionOperations.withoutTransaction(), configured);
    }

    private static KeycloakEvent login(String realm, String userId) {
        return new KeycloakEvent("e1", 1L, realm, "LOGIN", userId, null, Map.of("username", "break-glass"));
    }

    @Test
    void breakGlassLoginRaisesAnAlertAndIsAudited() {
        monitor(BREAK_GLASS).login(login("crm-admin", BREAK_GLASS));

        verify(alerts).raise(eq("break-glass-login"), contains(BREAK_GLASS));
        verify(audit).system(eq(AuditAction.BREAK_GLASS_LOGIN), eq("crm-admin"), eq(BREAK_GLASS), any(), any());
    }

    @Test
    void otherAdminLoginIsIgnored() {
        monitor(BREAK_GLASS).login(login("crm-admin", "someone-else"));

        verifyNoInteractions(alerts, audit);
    }

    @Test
    void sameIdInAnotherRealmIsIgnored() {
        monitor(BREAK_GLASS).login(login("crm-internal", BREAK_GLASS));

        verifyNoInteractions(alerts, audit);
    }

    @Test
    void nothingHappensWhenNoBreakGlassAccountIsConfigured() {
        monitor("").login(login("crm-admin", ""));

        verifyNoInteractions(alerts, audit);
        verify(alerts, org.mockito.Mockito.never()).raise(anyString(), anyString());
    }

    @Test
    void redeliveredEventAlertsOnce() {
        BreakGlassMonitor monitor = monitor(BREAK_GLASS);

        monitor.login(login("crm-admin", BREAK_GLASS));
        monitor.login(login("crm-admin", BREAK_GLASS));

        verify(alerts, org.mockito.Mockito.times(1)).raise(anyString(), anyString());
    }

    @Test
    void auditEventIdComesFromTheKeycloakEvent() {
        monitor(BREAK_GLASS).login(login("crm-admin", BREAK_GLASS));

        verify(audit)
                .system(
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(java.util.UUID.nameUUIDFromBytes(
                                "break-glass-login:e1".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
