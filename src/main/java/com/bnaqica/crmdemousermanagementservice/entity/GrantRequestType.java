package com.bnaqica.crmdemousermanagementservice.entity;

/** The privileged changes that need a second admin. */
public enum GrantRequestType {
    /** Assigning the Super Manager role; the target is the user's sub. */
    SUPER_MANAGER,
    /** Creating an admin account in crm-admin; the target is the new admin's email. */
    ADMIN_ACCOUNT
}
