package com.bnaqica.crmdemousermanagementservice.dto.response;

import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestStatus;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

/**
 * @param target the user's sub for SUPER_MANAGER, the new admin's email for ADMIN_ACCOUNT
 * @param status EXPIRED as soon as {@code expiresAt} has passed without a decision
 * @param details what will be applied
 */
@Builder
public record GrantRequestResponse(
        UUID id,
        GrantRequestType type,
        String target,
        GrantRequestStatus status,
        String requestedBy,
        String decidedBy,
        String ticketRef,
        JsonNode details,
        Instant createdAt,
        Instant decidedAt,
        Instant expiresAt) {}
