package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.entity.Permission;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.repository.PermissionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository repository;

    @Override
    @Transactional
    public Permission save(Permission permission) {
        return repository.saveAndFlush(permission);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Permission> findByService(String service) {
        return repository.findByService(service);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Permission> findAll() {
        return repository.findAll(Sort.by("service", "key"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Permission> find(UserType realm, String key) {
        return repository.findByRealmAndKey(realm, key);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Permission> findAll(UserType realm, Collection<String> keys) {
        return repository.findByRealmAndKeyIn(realm, keys);
    }
}
