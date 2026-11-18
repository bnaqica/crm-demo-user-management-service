package com.bnaqica.crmdemousermanagementservice.dto.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Published to {@code ums-user-events} after a committed membership or manager change. Keyed by {@code sub}. CRM
 * services evict their cached team scope for the user.
 *
 * @param member whether the user is in the team after the change
 * @param manager whether the user manages the team after the change
 */
@Builder
public record TeamMembershipChanged(
        String type,
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID sub,
        UUID teamId,
        boolean member,
        boolean manager) {}
