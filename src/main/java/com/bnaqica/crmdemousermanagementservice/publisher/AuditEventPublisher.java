package com.bnaqica.crmdemousermanagementservice.publisher;

import com.bnaqica.crmdemousermanagementservice.config.UmsProperties;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditEvent;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.service.OutboxService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit events for admin actions to the outbox. A success is written inside the transaction that makes the
 * change, so it exists only if the change commits; a failure is written in a transaction of its own, because the
 * failed change's transaction rolls back.
 */
@Slf4j
@Component
public class AuditEventPublisher {

    static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ATTRIBUTE = AuditEventPublisher.class.getName() + ".correlationId";

    private final OutboxService outbox;
    private final JsonMapper jsonMapper;
    private final UmsProperties properties;
    private final Clock clock;
    private final TransactionTemplate ownTransaction;

    public AuditEventPublisher(
            OutboxService outbox,
            JsonMapper jsonMapper,
            UmsProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Runs {@code action} and, if it throws, audits the failure before rethrowing. The action audits its own success. */
    public <T> T audited(AuditAction action, String targetId, String ticketRef, Supplier<T> work) {
        return run(work, () -> event(action, targetId, ticketRef));
    }

    /**
     * As {@link #audited}, for creating a user, which has no id until it succeeds. A failure is recorded with the
     * realm and {@code requested} as its {@code after}.
     */
    public <T> T auditedCreate(
            AuditAction action, UserType userType, Object requested, String ticketRef, Supplier<T> work) {
        return run(
                work,
                () -> event(action, null, ticketRef).realm(userType.getRealm()).after(requested));
    }

    private <T> T run(Supplier<T> work, Supplier<AuditEvent.AuditEventBuilder> failureEvent) {
        try {
            return work.get();
        } catch (RuntimeException ex) {
            failure(failureEvent, ex);
            throw ex;
        }
    }

    /** Call inside the transaction that makes the change. */
    public void success(
            AuditAction action, UserType userType, UUID target, String ticketRef, Object before, Object after) {
        publish(event(action, target.toString(), ticketRef)
                .realm(userType.getRealm())
                .outcome("SUCCESS")
                .before(before)
                .after(after)
                .build());
    }

    private void failure(Supplier<AuditEvent.AuditEventBuilder> failureEvent, RuntimeException cause) {
        try {
            AuditEvent event = failureEvent
                    .get()
                    .outcome("FAILURE")
                    .reason(cause.getMessage())
                    .build();
            ownTransaction.executeWithoutResult(status -> publish(event));
        } catch (RuntimeException ex) {
            // Never hide the original failure behind an audit failure
            log.error("Couldn't audit a failed admin action", ex);
        }
    }

    private AuditEvent.AuditEventBuilder event(AuditAction action, String targetId, String ticketRef) {
        HttpServletRequest request = currentRequest();
        return AuditEvent.builder()
                .eventId(UUID.randomUUID())
                .schemaVersion(1)
                .occurredAt(Instant.now(clock))
                .source("UMS")
                .actorId(actorId())
                .actorType("ADMIN")
                .action(action)
                .targetType("USER")
                .targetId(targetId)
                .ticketRef(ticketRef)
                .ipAddress(request == null ? null : request.getRemoteAddr())
                .correlationId(correlationId(request));
    }

    private void publish(AuditEvent event) {
        String key = event.targetId() == null ? event.eventId().toString() : event.targetId();
        outbox.add(properties.topics().auditEvents(), key, jsonMapper.writeValueAsString(event));
    }

    private static String actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getSubject();
        }
        return "anonymous"; // only in the local profile, where authentication is off
    }

    /** The caller's {@code X-Correlation-Id}, or one made up once per request so all its events share it. */
    private static String correlationId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        Object existing = request.getAttribute(CORRELATION_ATTRIBUTE);
        if (existing != null) {
            return existing.toString();
        }
        String header = request.getHeader(CORRELATION_HEADER);
        String id = header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
        request.setAttribute(CORRELATION_ATTRIBUTE, id);
        return id;
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
