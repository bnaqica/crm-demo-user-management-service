package com.bnaqica.crmdemousermanagementservice.dto.event;

import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Published to {@code ums-user-events} when the UMS creates or invites a user. Keyed by {@code sub}. */
@Builder
public record UserCreated(
        String type,
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID sub,
        UserType userType,
        String email,
        UserStatus status) {}
