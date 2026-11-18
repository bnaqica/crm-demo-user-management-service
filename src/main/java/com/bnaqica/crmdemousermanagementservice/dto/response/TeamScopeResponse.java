package com.bnaqica.crmdemousermanagementservice.dto.response;

import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Which teams a user may see CRM data for.
 *
 * @param memberTeams every team the user belongs to, managed or not
 * @param managedTeams the teams the user manages
 * @param allTeams true only for Super Managers
 */
@Builder
public record TeamScopeResponse(
        UUID sub, UserStatus status, List<UUID> memberTeams, List<UUID> managedTeams, boolean allTeams) {}
