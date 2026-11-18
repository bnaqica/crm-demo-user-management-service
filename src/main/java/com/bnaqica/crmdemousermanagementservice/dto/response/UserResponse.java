package com.bnaqica.crmdemousermanagementservice.dto.response;

import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record UserResponse(
        UUID sub,
        UserType userType,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        boolean multiTeamAllowed,
        Instant createdAt,
        Instant updatedAt) {}
