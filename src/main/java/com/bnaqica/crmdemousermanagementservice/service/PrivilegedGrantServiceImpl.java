package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.GrantRequestSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.request.CreateAdminRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.GrantRequestResponse;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequest;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestStatus;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestType;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.exception.GrantRequestClosedException;
import com.bnaqica.crmdemousermanagementservice.exception.SelfApprovalException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Approving first claims the request (PENDING to APPROVED, under a row lock, so a second approver gets 409), then
 * applies the change through the normal path; if applying fails, the request goes back to PENDING.
 */
@Slf4j
@Service
public class PrivilegedGrantServiceImpl implements PrivilegedGrantService {

    private final GrantRequestService grants;
    private final UserAccountService accounts;
    private final RoleService roles;
    private final RoleAssignments assignments;
    private final AdminAccounts admins;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Duration ttl;

    public PrivilegedGrantServiceImpl(
            GrantRequestService grants,
            UserAccountService accounts,
            RoleService roles,
            RoleAssignments assignments,
            AdminAccounts admins,
            AuditEventPublisher audit,
            TransactionOperations transactions,
            JsonMapper jsonMapper,
            Clock clock,
            @Value("${ums.grant-request-ttl:72h}") Duration ttl) {
        this.grants = grants;
        this.accounts = accounts;
        this.roles = roles;
        this.assignments = assignments;
        this.admins = admins;
        this.audit = audit;
        this.transactions = transactions;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public GrantRequestResponse requestSuperManager(UserAccount user, Role superManager, String ticketRef) {
        return open(
                GrantRequestType.SUPER_MANAGER,
                user.getSub().toString(),
                Map.of("role", superManager.getName()),
                user.getUserType().getRealm(),
                ticketRef);
    }

    @Override
    public GrantRequestResponse requestAdminAccount(CreateAdminRequest request) {
        return audit.audited(AuditAction.GRANT_REQUESTED, (String) null, request.ticketRef(), () -> {
            String email = EmailRules.normalise(request.email());
            admins.checkEmailFree(email);
            return open(
                    GrantRequestType.ADMIN_ACCOUNT,
                    email,
                    Map.of("email", email, "firstName", request.firstName(), "lastName", request.lastName()),
                    AdminAccounts.REALM,
                    request.ticketRef());
        });
    }

    private GrantRequestResponse open(
            GrantRequestType type, String target, Map<String, String> details, String realm, String ticketRef) {
        Instant now = Instant.now(clock);
        GrantRequest request = transactions.execute(status -> {
            GrantRequest created = grants.create(GrantRequest.builder()
                    .id(UUID.randomUUID())
                    .type(type)
                    .target(target)
                    .payload(jsonMapper.writeValueAsString(details))
                    .requestedBy(CurrentAdmin.sub())
                    .status(GrantRequestStatus.PENDING)
                    .ticketRef(ticketRef)
                    .createdAt(now)
                    .expiresAt(now.plus(ttl))
                    .build());
            audit.success(
                    AuditAction.GRANT_REQUESTED,
                    realm,
                    created.getId().toString(),
                    ticketRef,
                    null,
                    GrantRequestSnapshot.of(created));
            return created;
        });
        log.info("Opened grant request {} of type {} (ticket {})", request.getId(), type, ticketRef);
        return toResponse(request);
    }

    @Override
    public GrantRequestResponse approve(UUID id, String ticketRef) {
        return audit.audited(AuditAction.GRANT_REQUEST_APPROVED, id.toString(), ticketRef, () -> {
            String approver = CurrentAdmin.sub();
            GrantRequest claimed = decide(id, GrantRequestStatus.APPROVED, approver);
            try {
                apply(claimed);
            } catch (RuntimeException ex) {
                transactions.executeWithoutResult(status -> {
                    GrantRequest reopened = grants.lockForUpdate(id);
                    reopened.setStatus(GrantRequestStatus.PENDING);
                    reopened.setDecidedBy(null);
                    reopened.setDecidedAt(null);
                    grants.update(reopened);
                });
                throw ex;
            }
            transactions.executeWithoutResult(status -> audit.success(
                    AuditAction.GRANT_REQUEST_APPROVED,
                    realm(claimed),
                    id.toString(),
                    ticketRef,
                    pendingSnapshot(claimed),
                    GrantRequestSnapshot.of(claimed)));
            log.info("Grant request {} approved by {} (ticket {})", id, approver, ticketRef);
            return toResponse(grants.get(id));
        });
    }

    @Override
    public GrantRequestResponse reject(UUID id, String ticketRef) {
        return audit.audited(AuditAction.GRANT_REQUEST_REJECTED, id.toString(), ticketRef, () -> {
            GrantRequest rejected = decide(id, GrantRequestStatus.REJECTED, CurrentAdmin.sub());
            transactions.executeWithoutResult(status -> audit.success(
                    AuditAction.GRANT_REQUEST_REJECTED,
                    realm(rejected),
                    id.toString(),
                    ticketRef,
                    pendingSnapshot(rejected),
                    GrantRequestSnapshot.of(rejected)));
            log.info("Grant request {} rejected (ticket {})", id, ticketRef);
            return toResponse(rejected);
        });
    }

    /** Moves a PENDING request to {@code outcome}, or records that it expired and refuses. */
    private GrantRequest decide(UUID id, GrantRequestStatus outcome, String admin) {
        Decision decision = transactions.execute(status -> {
            GrantRequest locked = grants.lockForUpdate(id);
            if (locked.getStatus() != GrantRequestStatus.PENDING) {
                return new Decision(locked, false);
            }
            if (isExpired(locked)) {
                locked.setStatus(GrantRequestStatus.EXPIRED);
                return new Decision(grants.update(locked), false);
            }
            if (outcome == GrantRequestStatus.APPROVED
                    && locked.getRequestedBy().equals(admin)) {
                throw new SelfApprovalException();
            }
            locked.setStatus(outcome);
            locked.setDecidedBy(admin);
            locked.setDecidedAt(Instant.now(clock));
            return new Decision(grants.update(locked), true);
        });
        if (!decision.decided()) {
            throw new GrantRequestClosedException(
                    "The grant request is " + decision.request().getStatus());
        }
        return decision.request();
    }

    /** @param decided whether this call made the decision, rather than finding it closed */
    private record Decision(GrantRequest request, boolean decided) {}

    private void apply(GrantRequest request) {
        JsonNode details = jsonMapper.readTree(request.getPayload());
        switch (request.getType()) {
            case SUPER_MANAGER -> {
                UserAccount user = accounts.get(UUID.fromString(request.getTarget()));
                assignments.assign(user, roles.get(details.get("role").asString()), request.getTicketRef());
            }
            case ADMIN_ACCOUNT ->
                admins.create(
                        details.get("email").asString(),
                        details.get("firstName").asString(),
                        details.get("lastName").asString(),
                        request.getTicketRef());
        }
    }

    @Override
    public GrantRequestResponse get(UUID id) {
        return toResponse(grants.get(id));
    }

    @Override
    public List<GrantRequestResponse> list(GrantRequestStatus status) {
        return grants.findAll().stream()
                .map(this::toResponse)
                .filter(response -> status == null || response.status() == status)
                .toList();
    }

    private boolean isExpired(GrantRequest request) {
        return !Instant.now(clock).isBefore(request.getExpiresAt());
    }

    private GrantRequestResponse toResponse(GrantRequest request) {
        GrantRequestStatus status = request.getStatus() == GrantRequestStatus.PENDING && isExpired(request)
                ? GrantRequestStatus.EXPIRED
                : request.getStatus();
        return GrantRequestResponse.builder()
                .id(request.getId())
                .type(request.getType())
                .target(request.getTarget())
                .status(status)
                .requestedBy(request.getRequestedBy())
                .decidedBy(request.getDecidedBy())
                .ticketRef(request.getTicketRef())
                .details(jsonMapper.readTree(request.getPayload()))
                .createdAt(request.getCreatedAt())
                .decidedAt(request.getDecidedAt())
                .expiresAt(request.getExpiresAt())
                .build();
    }

    private static GrantRequestSnapshot pendingSnapshot(GrantRequest request) {
        return new GrantRequestSnapshot(
                request.getType(), request.getTarget(), GrantRequestStatus.PENDING, request.getRequestedBy(), null);
    }

    private static String realm(GrantRequest request) {
        return request.getType() == GrantRequestType.ADMIN_ACCOUNT ? AdminAccounts.REALM : "crm-internal";
    }
}
