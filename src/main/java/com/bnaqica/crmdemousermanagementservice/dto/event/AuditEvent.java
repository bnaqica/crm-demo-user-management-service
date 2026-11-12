package com.bnaqica.crmdemousermanagementservice.dto.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Published to {@code audit-events} for every admin write, whether it succeeded or failed. Follows the common audit
 * schema shared by Keycloak, UMS, CRM and portal records.
 *
 * @param before the target's state before the change, or null
 * @param after the target's state after the change (or what was requested, for a failed create), or null
 * @param reason why it failed; null on success
 */
@Builder
public record AuditEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        String source,
        String realm,
        String actorId,
        String actorType,
        AuditAction action,
        String targetType,
        String targetId,
        String outcome,
        String ticketRef,
        Object before,
        Object after,
        String ipAddress,
        String correlationId,
        String reason) {}
