package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.request.CreateInternalUserRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.InviteExternalUserRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserResponse;

/** Creates internal users and invites external users, in Keycloak and in the UMS. */
public interface UserOnboardingService {

    UserResponse createInternal(CreateInternalUserRequest request);

    UserResponse inviteExternal(InviteExternalUserRequest request);
}
