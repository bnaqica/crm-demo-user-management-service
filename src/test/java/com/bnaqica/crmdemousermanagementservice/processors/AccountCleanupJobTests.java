package com.bnaqica.crmdemousermanagementservice.processors;

import static org.assertj.core.api.Assertions.assertThat;

import com.bnaqica.crmdemousermanagementservice.entity.OutboxEvent;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.repository.OutboxEventRepository;
import com.bnaqica.crmdemousermanagementservice.service.AccountCleanup;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.KeycloakTestAdmin;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** starter-expiry spec: unpromoted starters expire and unused invites are cleaned up, at a fixed time. */
@SpringBootTest
@Import(IntegrationTestContainers.class)
class AccountCleanupJobTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** The jobs' "now"; users are dated relative to it. */
    private static final Instant NOW = Instant.parse("2030-01-31T03:00:00Z");

    @Autowired
    private AccountCleanup cleanup;

    @Autowired
    private AccountCleanupJob job;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private JdbcTemplate jdbc;

    private KeycloakTestAdmin kcAdmin;

    @BeforeEach
    void setUp() {
        kcAdmin = new KeycloakTestAdmin(keycloak);
    }

    /** A real Keycloak user with a UMS record in {@code status}, created {@code age} before {@link #NOW}. */
    private UUID user(String realm, UserType type, UserStatus status, Duration age) {
        String email = "cl-" + UUID.randomUUID().toString().substring(0, 8)
                + (type == UserType.INTERNAL ? "@corp.example" : "@acme.example");
        UUID sub = UUID.fromString(
                status == UserStatus.INVITED
                        ? kcAdmin.createUser(realm, email, List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP"))
                        : kcAdmin.createUser(realm, email));
        accounts.create(UserAccount.builder()
                .sub(sub)
                .userType(type)
                .email(email)
                .status(status)
                .createdAt(NOW.minus(age))
                .updatedAt(NOW.minus(age))
                .build());
        return sub;
    }

    private List<JsonNode> audits(UUID sub, String action) {
        return outbox.findAll().stream()
                .filter(event -> event.getTopic().equals("audit-events")
                        && event.getMessageKey().equals(sub.toString()))
                .map(OutboxEvent::getPayload)
                .map(JSON::readTree)
                .filter(event -> event.get("action").asString().equals(action))
                .toList();
    }

    @Test
    void starterPastExpiryIsDisabled() {
        UUID sub = user("portal", UserType.EXTERNAL, UserStatus.STARTER, Duration.ofDays(31));

        cleanup.expireStarters(NOW);

        UserAccount account = accounts.get(sub);
        assertThat(account.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(account.getStatusReason()).isEqualTo("STARTER_EXPIRED");
        assertThat(kcAdmin.isEnabled("portal", sub.toString())).isFalse();
        assertThat(audits(sub, "USER_DISABLED")).singleElement().satisfies(audit -> {
            assertThat(audit.get("actorType").asString()).isEqualTo("SYSTEM");
            assertThat(audit.get("after").get("reason").asString()).isEqualTo("STARTER_EXPIRED");
        });
    }

    @Test
    void starterWithinExpiryIsKept() {
        UUID sub = user("portal", UserType.EXTERNAL, UserStatus.STARTER, Duration.ofDays(29));

        cleanup.expireStarters(NOW);

        assertThat(accounts.get(sub).getStatus()).isEqualTo(UserStatus.STARTER);
    }

    @Test
    void expiredInviteIsRemoved() {
        UUID sub = user("crm-internal", UserType.INTERNAL, UserStatus.INVITED, Duration.ofDays(4));

        cleanup.removeExpiredInvites(NOW);

        assertThat(accounts.find(sub)).isEmpty();
        assertThat(kcAdmin.userExists("crm-internal", sub.toString())).isFalse();
        assertThat(audits(sub, "USER_DELETED"))
                .singleElement()
                .satisfies(audit ->
                        assertThat(audit.get("after").get("reason").asString()).isEqualTo("INVITE_EXPIRED"));
    }

    @Test
    void inviteStillValidIsKept() {
        UUID sub = user("crm-internal", UserType.INTERNAL, UserStatus.INVITED, Duration.ofDays(2));

        cleanup.removeExpiredInvites(NOW);

        assertThat(accounts.find(sub)).isPresent();
    }

    @Test
    void activeUserIsNeverRemoved() {
        UUID sub = user("crm-internal", UserType.INTERNAL, UserStatus.ACTIVE, Duration.ofDays(400));

        cleanup.removeExpiredInvites(NOW);
        cleanup.expireStarters(NOW);

        assertThat(accounts.get(sub).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void scheduledRunTakesTheShedLock() {
        job.run();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM crm_demo_user_management_service.shedlock WHERE name = 'account-cleanup'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void inviteeWhoSetAPasswordIsKept() {
        UUID sub = user("crm-internal", UserType.INTERNAL, UserStatus.INVITED, Duration.ofDays(4));
        kcAdmin.makeLoginReady("crm-internal", sub.toString()); // set the password; no LOGIN event reached the UMS

        cleanup.removeExpiredInvites(NOW);

        assertThat(accounts.find(sub)).isPresent();
        assertThat(kcAdmin.userExists("crm-internal", sub.toString())).isTrue();
    }

    @Test
    void starterPromotedSinceTheListWasReadIsKept() {
        UUID sub = user("portal", UserType.EXTERNAL, UserStatus.ACTIVE, Duration.ofDays(31));

        cleanup.expireStarter(sub, NOW);

        assertThat(accounts.get(sub).getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(kcAdmin.isEnabled("portal", sub.toString())).isTrue();
    }

    @Test
    void inviteeWhoSignedInSinceTheListWasReadIsKept() {
        UUID sub = user("crm-internal", UserType.INTERNAL, UserStatus.ACTIVE, Duration.ofDays(4));

        cleanup.removeExpiredInvite(sub, NOW);

        assertThat(accounts.find(sub)).isPresent();
    }
}
