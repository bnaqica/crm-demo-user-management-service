package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.UserSearchCriteria;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserPageResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserResponse;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/** CRUD for {@code user_account}. The only path to its repository. */
public interface UserAccountService {

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.EmailAlreadyInUseException if the email is taken */
    UserAccount create(UserAccount account);

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException if there is none */
    UserAccount get(UUID sub);

    Optional<UserAccount> find(UUID sub);

    /**
     * {@link #get}, locking the row until the caller's transaction ends. Needs a transaction.
     *
     * @throws com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException if there is none
     */
    UserAccount lockForUpdate(UUID sub);

    Optional<UserAccount> findByEmail(String email);

    /** The users with these subs that exist, in no particular order. */
    List<UserAccount> findAll(Collection<UUID> subs);

    UserAccount update(UserAccount account);

    void delete(UserAccount account);

    /** Users in {@code status} created before {@code before}. */
    List<UserAccount> findByStatusCreatedBefore(UserStatus status, Instant before);

    UserPageResponse search(UserSearchCriteria criteria, Pageable pageable);

    /** {@link #get} for the API. */
    UserResponse getResponse(UUID sub);
}
