package com.bnaqica.crmdemousermanagementservice.repository;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

    /** Case-insensitive, matching the unique {@code lower(email)} index so the lookup can use it. */
    @Query("SELECT u FROM UserAccount u WHERE lower(u.email) = lower(:email)")
    Optional<UserAccount> findByEmailIgnoreCase(String email);

    List<UserAccount> findByStatusAndCreatedAtBefore(UserStatus status, Instant before);

    /** {@code SELECT ... FOR UPDATE}: serialises changes to one user (team membership, cleanup). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.sub = :sub")
    Optional<UserAccount> findForUpdate(UUID sub);
}
