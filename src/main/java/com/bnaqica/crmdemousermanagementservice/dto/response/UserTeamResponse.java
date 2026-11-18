package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.util.UUID;
import lombok.Builder;

/** One team a user is in. */
@Builder
public record UserTeamResponse(UUID teamId, String teamName, boolean archived, boolean manager) {}
