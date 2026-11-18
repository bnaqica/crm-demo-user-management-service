package com.bnaqica.crmdemousermanagementservice.repository;

import com.bnaqica.crmdemousermanagementservice.entity.Team;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    /** {@code SELECT ... FOR SHARE}: other readers go on, but an UPDATE of the team waits. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT t FROM Team t WHERE t.id = :id")
    Optional<Team> findForShare(UUID id);
}
