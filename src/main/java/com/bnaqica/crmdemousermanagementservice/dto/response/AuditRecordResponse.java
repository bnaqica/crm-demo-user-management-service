package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

@Builder
public record AuditRecordResponse(
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
        JsonNode before,
        JsonNode after,
        String ipAddress,
        String correlationId,
        String reason) {}
