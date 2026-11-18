package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.TeamMemberCount;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamMemberResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserTeamResponse;
import com.bnaqica.crmdemousermanagementservice.entity.Team;
import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import java.util.Collection;

/** Maps teams and members to API responses. */
final class TeamMapper {

    private TeamMapper() {}

    static TeamResponse toResponse(Team team, TeamMemberCount count) {
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .archived(team.isArchived())
                .createdAt(team.getCreatedAt())
                .archivedAt(team.getArchivedAt())
                .memberCount(count == null ? 0 : count.members())
                .managerCount(count == null ? 0 : count.managers())
                .build();
    }

    static TeamResponse toResponse(Team team, Collection<TeamMember> members) {
        long managers = members.stream().filter(TeamMember::isManager).count();
        return toResponse(team, new TeamMemberCount(team.getId(), members.size(), managers));
    }

    /** {@code user} may be null only if the account vanished, which the foreign key prevents. */
    static TeamMemberResponse toResponse(TeamMember member, UserAccount user) {
        return TeamMemberResponse.builder()
                .teamId(member.getTeamId())
                .sub(member.getUserSub())
                .manager(member.isManager())
                .email(user == null ? null : user.getEmail())
                .firstName(user == null ? null : user.getFirstName())
                .lastName(user == null ? null : user.getLastName())
                .build();
    }

    static UserTeamResponse toUserTeam(TeamMember member, Team team) {
        return UserTeamResponse.builder()
                .teamId(member.getTeamId())
                .teamName(team.getName())
                .archived(team.isArchived())
                .manager(member.isManager())
                .build();
    }
}
