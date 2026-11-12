package com.bnaqica.crmdemousermanagementservice.repository;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

    /** Case-insensitive, matching the unique {@code lower(email)} index so the lookup can use it. */
    @Query("SELECT u FROM UserAccount u WHERE lower(u.email) = lower(:email)")
    Optional<UserAccount> findByEmailIgnoreCase(String email);
}
