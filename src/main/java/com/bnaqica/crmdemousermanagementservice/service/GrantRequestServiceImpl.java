package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.entity.GrantRequest;
import com.bnaqica.crmdemousermanagementservice.exception.GrantRequestNotFoundException;
import com.bnaqica.crmdemousermanagementservice.repository.GrantRequestRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GrantRequestServiceImpl implements GrantRequestService {

    private final GrantRequestRepository repository;

    @Override
    @Transactional
    public GrantRequest create(GrantRequest request) {
        return repository.saveAndFlush(request);
    }

    @Override
    @Transactional(readOnly = true)
    public GrantRequest get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new GrantRequestNotFoundException(id));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public GrantRequest lockForUpdate(UUID id) {
        return repository.findForUpdate(id).orElseThrow(() -> new GrantRequestNotFoundException(id));
    }

    @Override
    @Transactional
    public GrantRequest update(GrantRequest request) {
        return repository.saveAndFlush(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrantRequest> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
