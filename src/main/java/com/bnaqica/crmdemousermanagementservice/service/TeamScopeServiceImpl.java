package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.response.TeamScopeResponse;
import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamScopeServiceImpl implements TeamScopeService {

    private final UserAccountService accounts;
    private final TeamMemberService members;
    private final SuperManagerCheck superManagers;

    @Override
    public TeamScopeResponse scope(UUID sub) {
        UserAccount user = accounts.find(sub)
                .filter(account -> account.getUserType() == UserType.INTERNAL)
                .orElseThrow(() -> new UserNotFoundException(sub));
        List<TeamMember> teams = members.ofUser(sub);
        return TeamScopeResponse.builder()
                .sub(sub)
                .status(user.getStatus())
                .memberTeams(teams.stream().map(TeamMember::getTeamId).toList())
                .managedTeams(teams.stream()
                        .filter(TeamMember::isManager)
                        .map(TeamMember::getTeamId)
                        .toList())
                .allTeams(superManagers.isSuperManager(sub))
                .build();
    }
}
