package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.request.UpdateUserRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserResponse;
import java.util.UUID;

/** Admin actions on an existing account. Each one is done under a ticket reference. */
public interface UserSupportService {

    /** Disables the Keycloak account and revokes all its sessions. */
    UserResponse disable(UUID sub, String ticketRef);

    UserResponse enable(UUID sub, String ticketRef);

    /** Clears a brute-force lockout. */
    void unlock(UUID sub, String ticketRef);

    /** Revokes all of the user's sessions. */
    void logout(UUID sub, String ticketRef);

    /** Removes OTP and WebAuthn credentials so the user has to set up MFA again. */
    void resetMfa(UUID sub, String ticketRef);

    UserResponse update(UUID sub, UpdateUserRequest request);
}
