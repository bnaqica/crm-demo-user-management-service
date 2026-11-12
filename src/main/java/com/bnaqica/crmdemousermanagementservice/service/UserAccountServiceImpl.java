package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.UserSearchCriteria;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserPageResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserResponse;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.exception.EmailAlreadyInUseException;
import com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException;
import com.bnaqica.crmdemousermanagementservice.repository.UserAccountRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountRepository repository;

    @Override
    @Transactional
    public UserAccount create(UserAccount account) {
        try {
            return repository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyInUseException(); // the unique email index; a concurrent create won
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccount get(UUID sub) {
        return repository.findById(sub).orElseThrow(() -> new UserNotFoundException(sub));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> find(UUID sub) {
        return repository.findById(sub);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    @Override
    @Transactional
    public UserAccount update(UserAccount account) {
        try {
            return repository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyInUseException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserPageResponse search(UserSearchCriteria criteria, Pageable pageable) {
        Page<UserAccount> page = repository.findAll(matching(criteria), pageable);
        return new UserPageResponse(
                page.map(UserMapper::toResponse).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getResponse(UUID sub) {
        return UserMapper.toResponse(get(sub));
    }

    private static Specification<UserAccount> matching(UserSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.email() != null) {
                predicates.add(cb.like(cb.lower(root.get("email")), like(criteria.email())));
            }
            if (criteria.name() != null) {
                String name = like(criteria.name());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), name), cb.like(cb.lower(root.get("lastName")), name)));
            }
            if (criteria.userType() != null) {
                predicates.add(cb.equal(root.get("userType"), criteria.userType()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** A case-insensitive "contains" pattern, with LIKE wildcards in the input taken literally. */
    private static String like(String value) {
        String escaped = value.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
