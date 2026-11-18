package com.bnaqica.crmdemousermanagementservice.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * One row of the central audit store, in the common audit schema of Keycloak, UMS, CRM and portal records.
 * {@code before} and {@code after} are JSON text, already stripped of secrets.
 */
@Builder(toBuilder = true)
public record AuditRecord(
        UUID eventId,
        Instant occurredAt,
        String source,
        String realm,
        String actorId,
        String actorType,
        String action,
        String targetType,
        String targetId,
        String outcome,
        String ticketRef,
        String before,
        String after,
        String ipAddress,
        String correlationId,
        String reason) {}
