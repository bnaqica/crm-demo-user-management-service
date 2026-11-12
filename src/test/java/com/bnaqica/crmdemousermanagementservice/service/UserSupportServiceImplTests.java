package com.bnaqica.crmdemousermanagementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import com.bnaqica.crmdemousermanagementservice.dto.request.UpdateUserRequest;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.EmailAlreadyInUseException;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import com.bnaqica.crmdemousermanagementservice.publisher.UserEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

/** Failure paths between Keycloak and the database, which a real Keycloak can't be made to hit on demand. */
class UserSupportServiceImplTests {

    private static final UUID SUB = UUID.fromString("7c3f2d0e-0d39-4c39-9d0a-3a4c1f6f0b11");

    private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    private final UserAccountService accounts = mock(UserAccountService.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private UserSupportServiceImpl service;

    @BeforeEach
    void setUp() {
        var clients = new KeycloakClients(Map.of(UserType.INTERNAL, keycloak, UserType.EXTERNAL, keycloak));
        var properties = new UmsProperties(
                new UmsProperties.Keycloak("http://kc"),
                List.of("corp.example"),
                Duration.ofHours(72),
                List.of("external-standard"),
                null,
                null);
        service = new UserSupportServiceImpl(
                clients,
                accounts,
                new EmailRules(clients, accounts, properties),
                mock(UserEventPublisher.class),
                audit,
                TransactionOperations.withoutTransaction(),
                Clock.systemUTC());
        when(audit.audited(any(), any(), any(), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(3)).get());
        when(accounts.get(SUB)).thenReturn(account(UserStatus.ACTIVE));
        when(accounts.findByEmail(anyString())).thenReturn(Optional.empty());
        when(keycloak.findByEmail(anyString(), anyBoolean())).thenReturn(List.of());
        when(keycloak.getUser(SUB.toString()))
                .thenReturn(KeycloakUser.builder()
                        .id(SUB.toString())
                        .email("ana@corp.example")
                        .firstName("Ana")
                        .lastName("Test")
                        .emailVerified(true)
                        .requiredActions(List.of())
                        .build());
    }

    private static UserAccount account(UserStatus status) {
        return UserAccount.builder()
                .sub(SUB)
                .userType(UserType.INTERNAL)
                .email("ana@corp.example")
                .firstName("Ana")
                .lastName("Test")
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private static UpdateUserRequest rename() {
        return new UpdateUserRequest("ana.r@corp.example", "Ana", "Renamed", "OPS-1");
    }

    @Test
    void databaseFailureAfterKeycloakUpdateRestoresKeycloak() {
        when(accounts.update(any())).thenThrow(new ObjectOptimisticLockingFailureException(UserAccount.class, SUB));

        assertThatThrownBy(() -> service.update(SUB, rename()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        var updates = ArgumentCaptor.forClass(KeycloakUser.class);
        verify(keycloak, org.mockito.Mockito.times(2)).updateUser(eq(SUB.toString()), updates.capture());
        KeycloakUser restored = updates.getAllValues().get(1);
        assertThat(restored.email()).isEqualTo("ana@corp.example");
        assertThat(restored.lastName()).isEqualTo("Test");
        assertThat(restored.emailVerified()).isTrue();
        assertThat(restored.requiredActions()).isEmpty();
    }

    @Test
    void ownKeycloakRecordDoesNotCountAsEmailInUse() {
        when(keycloak.findByEmail("ana.r@corp.example", true))
                .thenReturn(List.of(KeycloakUser.builder().id(SUB.toString()).build()));
        when(accounts.update(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.update(SUB, rename()).email()).isEqualTo("ana.r@corp.example");
    }

    @Test
    void keycloakConflictOnUpdateIsEmailInUse() {
        doThrow(new KeycloakClientException(409, "User exists with same email"))
                .when(keycloak)
                .updateUser(eq(SUB.toString()), any());

        assertThatThrownBy(() -> service.update(SUB, rename())).isInstanceOf(EmailAlreadyInUseException.class);
        verify(accounts, never()).update(any());
    }

    @Test
    void keycloakFailureOnDisableLeavesStatusAlone() {
        doThrow(new KeycloakClientException(503, "down")).when(keycloak).updateUser(eq(SUB.toString()), any());

        assertThatThrownBy(() -> service.disable(SUB, "OPS-1")).isInstanceOf(KeycloakClientException.class);
        verify(accounts, never()).update(any());
    }
}
