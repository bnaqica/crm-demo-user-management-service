package com.bnaqica.crmdemousermanagementservice.dto.event;

import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** {@code RoleAssigned} or {@code RoleRevoked} on {@code ums-user-events}, keyed by {@code sub}. */
@Builder
public record RoleAssignmentChanged(
        String type, UUID eventId, int schemaVersion, Instant occurredAt, UUID sub, UserType userType, String role) {}
