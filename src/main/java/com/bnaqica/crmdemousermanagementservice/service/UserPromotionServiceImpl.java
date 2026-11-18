package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.UserSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.response.PromotionResponse;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.PromotionConflictException;
import com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import com.bnaqica.crmdemousermanagementservice.publisher.UserEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The role swap adds {@code external-standard} before removing {@code external-starter}, so a failure in between
 * leaves more access rather than none, and calling again finishes it. The status change, the event and the audit
 * are then written in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPromotionServiceImpl implements UserPromotionService {

    static final String STARTER = "external-starter";
    static final String STANDARD = "external-standard";

    private final UserAccountService accounts;
    private final RoleService roles;
    private final RoleKeycloakSync keycloakSync;
    private final PromotionRateLimiter rateLimiter;
    private final UserEventPublisher userEvents;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;
    private final MeterRegistry meters;
    private final Clock clock;

    @Override
    public PromotionResponse promote(UUID sub) {
        try {
            PromotionResponse response = audit.audited(
                    AuditAction.USER_PROMOTED, UserType.EXTERNAL, sub.toString(), null, () -> doPromote(sub));
            meters.counter("ums.promotions", "outcome", "success").increment();
            return response;
        } catch (RuntimeException ex) {
            meters.counter("ums.promotions", "outcome", "refused").increment();
            throw ex;
        }
    }

    private PromotionResponse doPromote(UUID sub) {
        rateLimiter.acquire();
        UserAccount user = accounts.find(sub).orElseThrow(() -> new UserNotFoundException(sub));
        if (user.getUserType() != UserType.EXTERNAL) {
            throw new PromotionConflictException("Only portal users can be promoted");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new PromotionConflictException("A disabled user can't be promoted");
        }
        Role starter = roles.get(STARTER);
        Role standard = roles.get(STANDARD);
        Set<String> held = keycloakSync.heldRoles(user, List.of(starter, standard));
        if (held.isEmpty()) {
            throw new PromotionConflictException("The user holds neither " + STARTER + " nor " + STANDARD);
        }
        UserSnapshot before = UserSnapshot.of(user);
        boolean change = held.contains(STARTER) || user.getStatus() == UserStatus.STARTER;
        if (held.contains(STARTER)) {
            if (!held.contains(STANDARD)) {
                keycloakSync.assign(user, standard);
            }
            keycloakSync.revoke(user, starter);
        }
        UserAccount saved = transactions.execute(status -> {
            UserAccount after = user;
            if (change) {
                if (user.getStatus() == UserStatus.STARTER) {
                    user.setStatus(UserStatus.ACTIVE);
                    user.setUpdatedAt(Instant.now(clock));
                }
                after = accounts.update(user);
                userEvents.userPromoted(after);
            }
            // Every call is audited; a repeat call records before == after and publishes nothing
            audit.success(AuditAction.USER_PROMOTED, UserType.EXTERNAL, sub, null, before, UserSnapshot.of(after));
            return after;
        });
        if (!change) {
            log.debug("User {} is promoted already", sub);
            return new PromotionResponse(sub, saved.getStatus(), List.of(STANDARD));
        }
        log.info("Promoted user {} to {}", sub, STANDARD);
        return new PromotionResponse(sub, saved.getStatus(), List.of(STANDARD));
    }
}
