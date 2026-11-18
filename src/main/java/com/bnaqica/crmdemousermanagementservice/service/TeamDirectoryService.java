package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.response.TeamMemberResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserTeamResponse;
import java.util.List;
import java.util.UUID;

/** Admin reads that combine teams, members and users. */
public interface TeamDirectoryService {

    /** Every team, archived ones included, by name, with member and manager counts. */
    List<TeamResponse> teams();

    TeamResponse team(UUID id);

    /** The team's members with their email and name. */
    List<TeamMemberResponse> members(UUID teamId);

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException if there is no such user */
    List<UserTeamResponse> teamsOf(UUID sub);
}
