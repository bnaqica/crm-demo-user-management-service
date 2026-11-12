package com.bnaqica.crmdemousermanagementservice.dto.event;

/** The admin actions the UMS audits. */
public enum AuditAction {
    USER_CREATED,
    USER_INVITED,
    USER_UPDATED,
    USER_DISABLED,
    USER_ENABLED,
    USER_UNLOCKED,
    USER_LOGGED_OUT,
    USER_MFA_RESET
}
