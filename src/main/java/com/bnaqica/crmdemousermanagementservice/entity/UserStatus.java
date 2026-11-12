package com.bnaqica.crmdemousermanagementservice.entity;

/**
 * Account status. Services treat anything other than ACTIVE (and STARTER for starter features) as no access.
 *
 * <p>Allowed transitions: INVITED to ACTIVE, STARTER to ACTIVE, ACTIVE to DISABLED and DISABLED to ACTIVE.
 */
public enum UserStatus {
    INVITED,
    STARTER,
    ACTIVE,
    DISABLED;

    public boolean canBecome(UserStatus next) {
        return switch (this) {
            case INVITED, STARTER, DISABLED -> next == ACTIVE;
            case ACTIVE -> next == DISABLED;
        };
    }
}
