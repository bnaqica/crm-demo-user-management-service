package com.bnaqica.crmdemousermanagementservice.repository;

import com.bnaqica.crmdemousermanagementservice.entity.GrantRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GrantRequestRepository extends JpaRepository<GrantRequest, UUID> {

    /** {@code SELECT ... FOR UPDATE}: two admins deciding at once are served one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GrantRequest g WHERE g.id = :id")
    Optional<GrantRequest> findForUpdate(UUID id);
}
