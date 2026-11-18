package com.bnaqica.crmdemousermanagementservice.dto;

import java.time.Instant;
import lombok.Builder;

/** Filters of an audit search; null means "any". {@code from} is inclusive, {@code to} exclusive. */
@Builder
public record AuditSearchCriteria(
        Instant from,
        Instant to,
        String source,
        String realm,
        String actorId,
        String targetId,
        String action,
        String outcome,
        String ticketRef,
        String correlationId) {}
