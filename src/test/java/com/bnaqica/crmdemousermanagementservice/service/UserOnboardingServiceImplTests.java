package com.bnaqica.crmdemousermanagementservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import com.bnaqica.crmdemousermanagementservice.dto.request.CreateInternalUserRequest;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.EmailAlreadyInUseException;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import com.bnaqica.crmdemousermanagementservice.publisher.UserEventPublisher;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The compensation path, which a real Keycloak can't be made to hit on demand: Keycloak fails after the user was
 * created there. Keycloak is the only thing mocked.
 */
class UserOnboardingServiceImplTests {

    private static final String ID = "7c3f2d0e-0d39-4c39-9d0a-3a4c1f6f0b11";

    private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    private final UserAccountService accounts = mock(UserAccountService.class);
    private final UserEventPublisher userEvents = mock(UserEventPublisher.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);

    private UserOnboardingServiceImpl service() {
        var clients = new KeycloakClients(Map.of(UserType.INTERNAL, keycloak, UserType.EXTERNAL, keycloak));
        var properties = new UmsProperties(
                new UmsProperties.Keycloak("http://kc"),
                List.of("corp.example"),
                Duration.ofHours(72),
                List.of("external-standard"),
                null,
                null);
        return new UserOnboardingServiceImpl(
                clients,
                accounts,
                properties,
                new EmailRules(clients, accounts, properties),
                userEvents,
                audit,
                TransactionOperations.withoutTransaction(),
                Clock.systemUTC());
    }

    private static ResponseEntity<Void> created() {
        return ResponseEntity.created(URI.create("http://kc/admin/realms/crm-internal/users/" + ID))
                .build();
    }

    private void emailFree() {
        when(accounts.findByEmail(anyString())).thenReturn(Optional.empty());
        when(keycloak.findByEmail(anyString(), anyBoolean())).thenReturn(List.of());
    }

    private static CreateInternalUserRequest request() {
        return new CreateInternalUserRequest("ana@corp.example", "Ana", "Test", "OPS-1");
    }

    @Test
    void databaseFailureAfterKeycloakCreateDeletesTheKeycloakUser() {
        when(audit.auditedCreate(any(), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(4)).get());
        emailFree();
        when(keycloak.create(any())).thenReturn(created());
        when(accounts.create(any())).thenThrow(new EmailAlreadyInUseException());

        assertThatThrownBy(() -> service().createInternal(request())).isInstanceOf(EmailAlreadyInUseException.class);

        verify(keycloak).deleteUser(ID);
    }

    @Test
    void missingLocationHeaderDeletesTheUserFoundByEmail() {
        when(audit.auditedCreate(any(), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(4)).get());
        emailFree();
        when(keycloak.create(any())).thenReturn(ResponseEntity.status(201).build());
        // Both realms share this mock: two empty answers for the email check, then the lookup after the create
        when(keycloak.findByEmail("ana@corp.example", true))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(KeycloakUser.builder().id(ID).build()));

        assertThatThrownBy(() -> service().createInternal(request())).isInstanceOf(KeycloakClientException.class);

        verify(keycloak).deleteUser(ID);
        verify(accounts, never()).create(any());
    }

    @Test
    void keycloakFailureAfterCreateLeavesNothingBehind() {
        when(audit.auditedCreate(any(), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(4)).get());
        when(accounts.findByEmail(anyString())).thenReturn(Optional.empty());
        when(keycloak.findByEmail(anyString(), anyBoolean())).thenReturn(List.of());
        when(keycloak.create(any())).thenReturn(created());
        doThrow(new KeycloakClientException(500, "SMTP down"))
                .when(keycloak)
                .executeActionsEmail(anyString(), anyInt(), anyList());

        assertThatThrownBy(() -> service()
                        .createInternal(new CreateInternalUserRequest("ana@corp.example", "Ana", "Test", "OPS-1")))
                .isInstanceOf(KeycloakClientException.class);

        verify(keycloak).deleteUser(ID);
        verify(accounts, never()).create(any());
        verify(userEvents, never()).userCreated(any());
    }
}
