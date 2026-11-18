package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.TeamMemberCount;
import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CRUD for {@code team_member}. The only path to its repository. */
public interface TeamMemberService {

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.TeamConflictException if the user is already in the team */
    TeamMember add(TeamMember member);

    Optional<TeamMember> find(UUID teamId, UUID sub);

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.TeamNotFoundException if the user isn't in the team */
    TeamMember get(UUID teamId, UUID sub);

    TeamMember update(TeamMember member);

    void remove(TeamMember member);

    /** Every team the user is in. */
    List<TeamMember> ofUser(UUID sub);

    List<TeamMember> ofTeam(UUID teamId);

    /** Member and manager counts of every team that has members. */
    List<TeamMemberCount> countByTeam();
}
