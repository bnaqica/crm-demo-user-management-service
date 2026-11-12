package com.bnaqica.crmdemousermanagementservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakCredential;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import com.bnaqica.crmdemousermanagementservice.support.KeycloakTestAdmin;
import com.bnaqica.crmdemousermanagementservice.support.TestTokens;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** user-accounts spec: disable, enable, unlock, force logout, MFA reset and profile updates. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class UserSupportApiTests {

    private static final String REALM = "crm-internal";
    private static final Map<String, String> TICKET = Map.of("ticketRef", "OPS-40");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private KeycloakClients keycloakClients;

    @Autowired
    private UserAccountService accounts;

    private AdminApi api;
    private KeycloakTestAdmin kcAdmin;
    private String email;

    @BeforeEach
    void setUp() {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        kcAdmin = new KeycloakTestAdmin(keycloak);
        email = "s-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.example";
    }

    /** An internal user who has finished setup: ACTIVE, with a password and a live session. */
    private String activeUserWithSession() throws Exception {
        String sub = api.createInternal(email);
        kcAdmin.makeLoginReady(REALM, sub);
        setStatus(sub, UserStatus.ACTIVE);
        assertThat(kcAdmin.login(REALM, email, TestTokens.PASSWORD)).isEqualTo(200);
        return sub;
    }

    private void setStatus(String sub, UserStatus status) {
        UserAccount account = accounts.get(UUID.fromString(sub));
        account.setStatus(status);
        accounts.update(account);
    }

    private KeycloakUser kcUser(String sub) {
        return keycloakClients.forType(UserType.INTERNAL).getUser(sub);
    }

    @Test
    void disablingAnInvitedUserIsAConflict() throws Exception {
        String sub = api.createInternal(email);

        api.post("/admin/users/" + sub + "/disable", TICKET).andExpect(status().isConflict());
    }

    @Test
    void userDisabled() throws Exception {
        String sub = activeUserWithSession();

        api.post("/admin/users/" + sub + "/disable", TICKET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertThat(kcUser(sub).enabled()).isFalse();
        assertThat(kcAdmin.sessionCount(REALM, sub)).isZero();
    }

    @Test
    void disabledUserEnabledAgain() throws Exception {
        String sub = activeUserWithSession();
        api.post("/admin/users/" + sub + "/disable", TICKET).andExpect(status().isOk());

        api.post("/admin/users/" + sub + "/enable", TICKET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(kcUser(sub).enabled()).isTrue();
    }

    @Test
    void actionWithoutTicketIsRejected() throws Exception {
        String sub = activeUserWithSession();

        api.post("/admin/users/" + sub + "/disable", Map.of()).andExpect(status().isBadRequest());

        assertThat(kcUser(sub).enabled()).isTrue();
    }

    @Test
    void lockedUserUnlocked() throws Exception {
        String sub = activeUserWithSession();
        for (int i = 0; i < 5; i++) {
            kcAdmin.login(REALM, email, "wrong-password-" + i);
        }
        assertThat(kcAdmin.isLocked(REALM, sub)).isTrue();

        api.post("/admin/users/" + sub + "/unlock", TICKET).andExpect(status().isOk());

        assertThat(kcAdmin.isLocked(REALM, sub)).isFalse();
        assertThat(kcAdmin.login(REALM, email, TestTokens.PASSWORD)).isEqualTo(200);
    }

    @Test
    void forceLogout() throws Exception {
        String sub = activeUserWithSession();

        api.post("/admin/users/" + sub + "/logout", TICKET).andExpect(status().isOk());

        assertThat(kcAdmin.sessionCount(REALM, sub)).isZero();
    }

    @Test
    void mfaReset() throws Exception {
        String sub = kcAdmin.importUserWithOtp(REALM, email);
        accounts.create(UserAccount.builder()
                .sub(UUID.fromString(sub))
                .userType(UserType.INTERNAL)
                .email(email)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        assertThat(keycloakClients.forType(UserType.INTERNAL).credentials(sub))
                .extracting(KeycloakCredential::type)
                .contains("otp");

        api.post("/admin/users/" + sub + "/reset-mfa", Map.of("ticketRef", "OPS-77"))
                .andExpect(status().isOk());

        assertThat(keycloakClients.forType(UserType.INTERNAL).credentials(sub))
                .extracting(KeycloakCredential::type)
                .doesNotContain("otp", "webauthn", "webauthn-passwordless");
        assertThat(kcUser(sub).requiredActions()).contains("CONFIGURE_TOTP");
    }

    @Test
    void emailChanged() throws Exception {
        String sub = activeUserWithSession();
        String newEmail = "new-" + email;

        api.put(
                        "/admin/users/" + sub,
                        Map.of(
                                "firstName",
                                "Ana",
                                "lastName",
                                "Renamed",
                                "email",
                                newEmail.toUpperCase(),
                                "ticketRef",
                                "OPS-41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.lastName").value("Renamed"))
                .andExpect(jsonPath("$.sub").value(sub));

        KeycloakUser user = kcUser(sub);
        assertThat(user.email()).isEqualTo(newEmail);
        assertThat(user.emailVerified()).isFalse();
        assertThat(user.requiredActions()).contains("VERIFY_EMAIL");
    }

    @Test
    void oldEmailIsFreeAfterARename() throws Exception {
        String sub = activeUserWithSession();
        String newEmail = "renamed-" + email;
        api.put(
                        "/admin/users/" + sub,
                        Map.of("firstName", "Ana", "lastName", "Test", "email", newEmail, "ticketRef", "OPS-44"))
                .andExpect(status().isOk());

        assertThat(kcUser(sub).username()).isEqualTo(newEmail);
        api.post(
                        "/admin/users/internal",
                        Map.of("email", email, "firstName", "New", "lastName", "Person", "ticketRef", "OPS-45"))
                .andExpect(status().isCreated());
    }

    @Test
    void retryingAnUpdateThatKeycloakAlreadyAppliedSucceeds() throws Exception {
        String sub = activeUserWithSession();
        String newEmail = "retry-" + email;
        // As if an earlier attempt changed Keycloak but failed before the UMS saved it
        keycloakClients
                .forType(UserType.INTERNAL)
                .updateUser(sub, KeycloakUser.builder().email(newEmail).build());

        api.put(
                        "/admin/users/" + sub,
                        Map.of("firstName", "Ana", "lastName", "Test", "email", newEmail, "ticketRef", "OPS-46"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    @Test
    void emailChangedToOneInUse() throws Exception {
        String sub = activeUserWithSession();
        String other = "other-" + email;
        api.createInternal(other);

        api.put(
                        "/admin/users/" + sub,
                        Map.of("firstName", "Ana", "lastName", "Test", "email", other, "ticketRef", "OPS-42"))
                .andExpect(status().isConflict());

        assertThat(kcUser(sub).email()).isEqualTo(email);
    }

    @Test
    void unknownUser() throws Exception {
        api.post("/admin/users/" + UUID.randomUUID() + "/unlock", TICKET).andExpect(status().isNotFound());
    }
}
