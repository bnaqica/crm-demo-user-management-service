package com.bnaqica.crmdemousermanagementservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakRole;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionOperations;

/** A half-created admin account is removed again, so a retried approval can succeed. */
class AdminAccountsTests {

    private final KeycloakAdminClient admin = mock(KeycloakAdminClient.class);
    private final UmsProperties properties = new UmsProperties(null, null, Duration.ofHours(72), null, null, null);
    private final AdminAccounts accounts = new AdminAccounts(
            new KeycloakClients(Map.of(), admin),
            properties,
            mock(EmailRules.class),
            mock(AuditEventPublisher.class),
            TransactionOperations.withoutTransaction());

    @Test
    void failureAfterCreatingTheUserDeletesIt() {
        when(admin.findByEmail(anyString(), anyBoolean())).thenReturn(List.of());
        when(admin.create(any()))
                .thenReturn(ResponseEntity.created(URI.create("http://kc/admin/realms/crm-admin/users/new-id"))
                        .build());
        when(admin.availableRealmRoles("new-id")).thenReturn(List.of(new KeycloakRole("r1", "admin")));
        doThrow(new KeycloakClientException(500, "mail down"))
                .when(admin)
                .executeActionsEmail(anyString(), org.mockito.ArgumentMatchers.anyInt(), any());

        assertThatThrownBy(() -> accounts.create("new@crm.local", "New", "Admin", "OPS-1"))
                .isInstanceOf(KeycloakClientException.class);

        verify(admin).deleteUser("new-id");
    }
}
