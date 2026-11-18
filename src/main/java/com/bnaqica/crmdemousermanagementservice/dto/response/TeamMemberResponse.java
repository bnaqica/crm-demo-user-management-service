package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.util.UUID;
import lombok.Builder;

@Builder
public record TeamMemberResponse(
        UUID teamId, UUID sub, boolean manager, String email, String firstName, String lastName) {}
