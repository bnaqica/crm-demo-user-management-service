package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.TeamMemberCount;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamMemberResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserTeamResponse;
import com.bnaqica.crmdemousermanagementservice.entity.Team;
import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamDirectoryServiceImpl implements TeamDirectoryService {

    private final TeamService teams;
    private final TeamMemberService members;
    private final UserAccountService accounts;

    @Override
    public List<TeamResponse> teams() {
        Map<UUID, TeamMemberCount> counts =
                members.countByTeam().stream().collect(Collectors.toMap(TeamMemberCount::teamId, Function.identity()));
        return teams.findAll().stream()
                .map(team -> TeamMapper.toResponse(team, counts.get(team.getId())))
                .toList();
    }

    @Override
    public TeamResponse team(UUID id) {
        return TeamMapper.toResponse(teams.get(id), members.ofTeam(id));
    }

    @Override
    public List<TeamMemberResponse> members(UUID teamId) {
        teams.get(teamId); // 404 for an unknown team
        List<TeamMember> inTeam = members.ofTeam(teamId);
        Map<UUID, UserAccount> users = accounts
                .findAll(inTeam.stream().map(TeamMember::getUserSub).toList())
                .stream()
                .collect(Collectors.toMap(UserAccount::getSub, Function.identity()));
        return inTeam.stream()
                .map(member -> TeamMapper.toResponse(member, users.get(member.getUserSub())))
                .sorted(Comparator.comparing(TeamMemberResponse::email, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Override
    public List<UserTeamResponse> teamsOf(UUID sub) {
        accounts.get(sub); // 404 for an unknown user
        List<TeamMember> memberships = members.ofUser(sub);
        Map<UUID, Team> byId = teams
                .findAll(memberships.stream().map(TeamMember::getTeamId).toList())
                .stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        return memberships.stream()
                .map(member -> TeamMapper.toUserTeam(member, byId.get(member.getTeamId())))
                .sorted(Comparator.comparing(UserTeamResponse::teamName))
                .toList();
    }
}
