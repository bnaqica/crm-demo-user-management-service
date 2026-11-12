package com.bnaqica.crmdemousermanagementservice.dto;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;

/** A user's audited fields at one moment, for the before and after of an audit event. */
public record UserSnapshot(UserType userType, String email, String firstName, String lastName, UserStatus status) {

    public static UserSnapshot of(UserAccount account) {
        return new UserSnapshot(
                account.getUserType(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getStatus());
    }
}
