package com.bnaqica.crmdemousermanagementservice.dto.event;

import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Published to {@code ums-user-events} when a portal starter becomes a standard user. Keyed by {@code sub}. */
@Builder
public record UserPromoted(
        String type, UUID eventId, int schemaVersion, Instant occurredAt, UUID sub, UserType userType, String email) {}
