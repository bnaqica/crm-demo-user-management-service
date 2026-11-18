package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.exception.RoleConflictException;
import com.bnaqica.crmdemousermanagementservice.exception.RoleNotFoundException;
import com.bnaqica.crmdemousermanagementservice.repository.RoleRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository repository;

    @Override
    @Transactional
    public Role create(Role role) {
        try {
            return repository.saveAndFlush(role);
        } catch (DataIntegrityViolationException ex) {
            throw new RoleConflictException("A role with this name already exists"); // the unique name index
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Role get(String name) {
        return find(name).orElseThrow(() -> new RoleNotFoundException(name));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> find(String name) {
        return repository.findByNameIgnoreCase(name);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Role lockForUpdate(String name) {
        return repository.findForUpdate(name).orElseThrow(() -> new RoleNotFoundException(name));
    }

    @Override
    @Transactional
    public Role update(Role role) {
        return repository.saveAndFlush(role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return repository.findAll(Sort.by("name"));
    }
}
