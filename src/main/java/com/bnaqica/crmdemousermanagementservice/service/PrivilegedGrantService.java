package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.request.CreateAdminRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.GrantRequestResponse;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestStatus;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import java.util.List;
import java.util.UUID;

/** Privileged changes that a second admin must approve: Super Manager and new admin accounts. */
public interface PrivilegedGrantService {

    GrantRequestResponse requestSuperManager(UserAccount user, Role superManager, String ticketRef);

    GrantRequestResponse requestAdminAccount(CreateAdminRequest request);

    /**
     * Applies the change and closes the request as APPROVED.
     *
     * @throws com.bnaqica.crmdemousermanagementservice.exception.SelfApprovalException if the caller made the request
     * @throws com.bnaqica.crmdemousermanagementservice.exception.GrantRequestClosedException if it isn't PENDING, or has expired
     */
    GrantRequestResponse approve(UUID id, String ticketRef);

    /** Closes the request without effect. Any admin may reject, the requester included. */
    GrantRequestResponse reject(UUID id, String ticketRef);

    GrantRequestResponse get(UUID id);

    /** Newest first; {@code status} null for all. PENDING lists only requests that haven't expired. */
    List<GrantRequestResponse> list(GrantRequestStatus status);
}
