package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.TeamMemberCount;
import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import com.bnaqica.crmdemousermanagementservice.exception.TeamConflictException;
import com.bnaqica.crmdemousermanagementservice.exception.TeamNotFoundException;
import com.bnaqica.crmdemousermanagementservice.repository.TeamMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamMemberServiceImpl implements TeamMemberService {

    private final TeamMemberRepository repository;

    @Override
    @Transactional
    public TeamMember add(TeamMember member) {
        // An assigned id makes save() a merge, which would overwrite an existing row rather than fail
        if (repository.existsById(member.getId())) {
            throw new TeamConflictException("The user is already in this team");
        }
        return repository.saveAndFlush(member);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TeamMember> find(UUID teamId, UUID sub) {
        return repository.findById(new TeamMember.Key(teamId, sub));
    }

    @Override
    @Transactional(readOnly = true)
    public TeamMember get(UUID teamId, UUID sub) {
        return find(teamId, sub).orElseThrow(() -> new TeamNotFoundException(teamId, sub));
    }

    @Override
    @Transactional
    public TeamMember update(TeamMember member) {
        return repository.saveAndFlush(member);
    }

    @Override
    @Transactional
    public void remove(TeamMember member) {
        repository.delete(member);
        repository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMember> ofUser(UUID sub) {
        return repository.findByIdUserSub(sub);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMember> ofTeam(UUID teamId) {
        return repository.findByIdTeamId(teamId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberCount> countByTeam() {
        return repository.countByTeam();
    }
}
