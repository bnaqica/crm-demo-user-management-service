package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record TeamResponse(
        UUID id,
        String name,
        boolean archived,
        Instant createdAt,
        Instant archivedAt,
        long memberCount,
        long managerCount) {}
