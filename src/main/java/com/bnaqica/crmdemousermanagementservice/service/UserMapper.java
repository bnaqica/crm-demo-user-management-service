package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.response.UserResponse;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;

/** Maps user accounts to API responses. */
final class UserMapper {

    private UserMapper() {}

    static UserResponse toResponse(UserAccount account) {
        return UserResponse.builder()
                .sub(account.getSub())
                .userType(account.getUserType())
                .email(account.getEmail())
                .firstName(account.getFirstName())
                .lastName(account.getLastName())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
