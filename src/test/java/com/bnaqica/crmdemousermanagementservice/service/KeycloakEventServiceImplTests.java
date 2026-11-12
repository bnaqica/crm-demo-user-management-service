package com.bnaqica.crmdemousermanagementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidKeycloakEventException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The cases the Kafka tests don't cover: LOGIN events that must change nothing, and unusable events. */
class KeycloakEventServiceImplTests {

    private final UserAccountService accounts = mock(UserAccountService.class);
    private final KeycloakEventServiceImpl service = new KeycloakEventServiceImpl(
            JsonMapper.builder().build(), accounts, mock(LockoutMonitor.class), Clock.systemUTC());

    private static String login(Object userId) {
        return """
                {"id":"%s","time":1,"realm":"crm-internal","type":"LOGIN","userId":"%s"}""".formatted(UUID.randomUUID(), userId);
    }

    @Test
    void loginOfDisabledUserKeepsItDisabled() {
        UUID sub = UUID.randomUUID();
        UserAccount account = UserAccount.builder()
                .sub(sub)
                .userType(UserType.INTERNAL)
                .status(UserStatus.DISABLED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(accounts.find(sub)).thenReturn(Optional.of(account));

        service.handle(login(sub));

        assertThat(account.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(accounts, never()).update(any());
    }

    @Test
    void loginOfUnknownUserIsIgnored() {
        UUID sub = UUID.randomUUID();
        when(accounts.find(sub)).thenReturn(Optional.empty());

        service.handle(login(sub));

        verify(accounts, never()).update(any());
    }

    @Test
    void eventWithoutIdIsInvalid() {
        assertThatThrownBy(() -> service.handle("{\"type\":\"LOGIN\"}"))
                .isInstanceOf(InvalidKeycloakEventException.class);
    }
}
