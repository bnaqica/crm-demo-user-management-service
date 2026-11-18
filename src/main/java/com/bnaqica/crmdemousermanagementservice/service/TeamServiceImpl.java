package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.entity.Team;
import com.bnaqica.crmdemousermanagementservice.exception.TeamConflictException;
import com.bnaqica.crmdemousermanagementservice.exception.TeamNotFoundException;
import com.bnaqica.crmdemousermanagementservice.repository.TeamRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamRepository repository;

    @Override
    @Transactional
    public Team create(Team team) {
        try {
            return repository.saveAndFlush(team);
        } catch (DataIntegrityViolationException ex) {
            throw new TeamConflictException("A team with this name already exists"); // the unique name index
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Team get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TeamNotFoundException(id));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Team lockForShare(UUID id) {
        return repository.findForShare(id).orElseThrow(() -> new TeamNotFoundException(id));
    }

    @Override
    @Transactional
    public Team update(Team team) {
        return repository.saveAndFlush(team);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Team> findAll() {
        return repository.findAll(Sort.by("name"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Team> findAll(Collection<UUID> ids) {
        return repository.findAllById(ids);
    }
}
