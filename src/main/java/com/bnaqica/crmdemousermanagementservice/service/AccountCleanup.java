package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.KeycloakUser;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import com.bnaqica.crmdemousermanagementservice.publisher.UserEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Removes accounts nobody activated: starters not promoted in time are disabled, and invited users whose invite
 * expired before they ever signed in are deleted. Each account is handled on its own, so one failure doesn't stop
 * the rest; the next run retries it. {@code now} is passed in so the schedule and tests decide the time.
 */
@Slf4j
@Service
public class AccountCleanup {

    static final String STARTER_EXPIRED = "STARTER_EXPIRED";
    static final String INVITE_EXPIRED = "INVITE_EXPIRED";

    private final UserAccountService accounts;
    private final TeamMemberService members;
    private final KeycloakClients keycloak;
    private final UserEventPublisher userEvents;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;
    private final Duration inviteLifespan;
    private final Duration starterExpiry;

    public AccountCleanup(
            UserAccountService accounts,
            TeamMemberService members,
            KeycloakClients keycloak,
            UserEventPublisher userEvents,
            AuditEventPublisher audit,
            TransactionOperations transactions,
            UmsProperties properties,
            @Value("${ums.cleanup.starter-expiry-days:30}") int starterExpiryDays) {
        this.accounts = accounts;
        this.members = members;
        this.keycloak = keycloak;
        this.userEvents = userEvents;
        this.audit = audit;
        this.transactions = transactions;
        this.inviteLifespan = properties.inviteLifespan();
        this.starterExpiry = Duration.ofDays(starterExpiryDays);
    }

    /** Disables STARTER users registered more than the expiry before {@code now}. */
    public void expireStarters(Instant now) {
        for (UserAccount starter : accounts.findByStatusCreatedBefore(UserStatus.STARTER, now.minus(starterExpiry))) {
            try {
                expireStarter(starter.getSub(), now);
            } catch (RuntimeException ex) {
                log.error("Couldn't expire starter {}; the next run tries again", starter.getSub(), ex);
            }
        }
    }

    /** Deletes INVITED users whose invite had expired by {@code now} without a first login. */
    public void removeExpiredInvites(Instant now) {
        for (UserAccount invited : accounts.findByStatusCreatedBefore(UserStatus.INVITED, now.minus(inviteLifespan))) {
            try {
                removeExpiredInvite(invited.getSub(), now);
            } catch (RuntimeException ex) {
                log.error("Couldn't remove expired invite of {}; the next run tries again", invited.getSub(), ex);
            }
        }
    }

    /**
     * Disables one starter, if it is still an unpromoted starter past the expiry. The row stays locked while
     * Keycloak is changed, so a promotion in progress waits, and one that came first is seen.
     */
    public void expireStarter(UUID sub, Instant now) {
        transactions.executeWithoutResult(status -> {
            UserAccount starter = accounts.lockForUpdate(sub);
            if (starter.getStatus() != UserStatus.STARTER
                    || !starter.getCreatedAt().isBefore(now.minus(starterExpiry))) {
                log.debug("User {} is no longer an expired starter; skipped", sub);
                return;
            }
            KeycloakAdminClient client = keycloak.forType(starter.getUserType());
            client.updateUser(
                    sub.toString(), KeycloakUser.builder().enabled(false).build());
            client.logout(sub.toString());
            starter.setStatus(UserStatus.DISABLED);
            starter.setStatusReason(STARTER_EXPIRED);
            starter.setUpdatedAt(now);
            UserAccount disabled = accounts.update(starter);
            userEvents.userDisabled(disabled);
            audit.system(
                    AuditAction.USER_DISABLED,
                    disabled.getUserType().getRealm(),
                    sub.toString(),
                    Map.of("status", UserStatus.DISABLED, "reason", STARTER_EXPIRED));
            log.info("Disabled starter {} ({})", sub, STARTER_EXPIRED);
        });
    }

    /**
     * Deletes one invited user, if still INVITED past the invite's lifespan and Keycloak still shows the invite's
     * actions as undone. A user who set their password is kept: their first login (and its LOGIN event) may be late.
     */
    public void removeExpiredInvite(UUID sub, Instant now) {
        transactions.executeWithoutResult(status -> {
            UserAccount invited = accounts.lockForUpdate(sub);
            if (invited.getStatus() != UserStatus.INVITED
                    || !invited.getCreatedAt().isBefore(now.minus(inviteLifespan))) {
                log.debug("User {} is no longer an expired invite; skipped", sub);
                return;
            }
            KeycloakAdminClient client = keycloak.forType(invited.getUserType());
            if (!stillUnused(client, sub)) {
                log.info("Invited user {} has set a password; kept until their first login", sub);
                return;
            }
            try {
                client.deleteUser(sub.toString());
            } catch (KeycloakClientException ex) {
                if (!ex.isNotFound()) {
                    throw ex;
                }
            }
            for (TeamMember member : members.ofUser(sub)) {
                members.remove(member);
                userEvents.teamMembershipChanged(sub, member.getTeamId(), false, false);
            }
            accounts.delete(invited);
            audit.system(
                    AuditAction.USER_DELETED,
                    invited.getUserType().getRealm(),
                    sub.toString(),
                    Map.of("status", UserStatus.INVITED, "reason", INVITE_EXPIRED));
            log.info("Deleted invited user {} ({})", sub, INVITE_EXPIRED);
        });
    }

    /** The invite's first action, setting a password, is still pending (or the Keycloak account is gone). */
    private static boolean stillUnused(KeycloakAdminClient client, UUID sub) {
        try {
            List<String> pending = client.getUser(sub.toString()).requiredActions();
            return pending != null && pending.contains("UPDATE_PASSWORD");
        } catch (KeycloakClientException ex) {
            if (ex.isNotFound()) {
                return true;
            }
            throw ex;
        }
    }
}
