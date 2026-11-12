package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.UserSearchCriteria;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserPageResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserResponse;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
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

    Optional<UserAccount> findByEmail(String email);

    UserAccount update(UserAccount account);

    UserPageResponse search(UserSearchCriteria criteria, Pageable pageable);

    /** {@link #get} for the API. */
    UserResponse getResponse(UUID sub);
}
