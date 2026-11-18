package com.bnaqica.crmdemousermanagementservice.dto.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The admin actions the UMS audits, each with the type of thing it acts on (the audit event's {@code targetType}). */
@Getter
@RequiredArgsConstructor
public enum AuditAction {
    USER_CREATED(Target.USER),
    USER_INVITED(Target.USER),
    USER_UPDATED(Target.USER),
    USER_DISABLED(Target.USER),
    USER_ENABLED(Target.USER),
    USER_UNLOCKED(Target.USER),
    USER_LOGGED_OUT(Target.USER),
    USER_MFA_RESET(Target.USER),
    USER_MULTI_TEAM_CHANGED(Target.USER),
    /** A portal starter became a standard user, by a system call. */
    USER_PROMOTED(Target.USER),
    /** An invited user who never signed in was removed after the invite expired. */
    USER_DELETED(Target.USER),
    TEAM_CREATED(Target.TEAM),
    TEAM_ARCHIVED(Target.TEAM),
    /** Membership actions target the user; the team is in {@code before} and {@code after}. */
    TEAM_MEMBER_ADDED(Target.USER),
    TEAM_MEMBER_REMOVED(Target.USER),
    TEAM_MANAGER_SET(Target.USER),
    TEAM_MANAGER_UNSET(Target.USER),
    ROLE_CREATED(Target.ROLE),
    ROLE_UPDATED(Target.ROLE),
    ROLE_ASSIGNED(Target.USER),
    ROLE_REVOKED(Target.USER),
    /** A privileged change was asked for; the target is the grant request. */
    GRANT_REQUESTED(Target.GRANT_REQUEST),
    GRANT_REQUEST_APPROVED(Target.GRANT_REQUEST),
    GRANT_REQUEST_REJECTED(Target.GRANT_REQUEST),
    /** An admin account was created in crm-admin, after approval. */
    ADMIN_CREATED(Target.ADMIN),
    ADMIN_DISABLED(Target.ADMIN),
    ADMIN_DELETED(Target.ADMIN),
    /** The break-glass admin signed in. */
    BREAK_GLASS_LOGIN(Target.ADMIN),
    /** A service registered its permissions; the target is the service. */
    CATALOG_REGISTERED(Target.CATALOG);

    private final String targetType;

    private static final class Target {
        static final String USER = "USER";
        static final String TEAM = "TEAM";
        static final String CATALOG = "CATALOG";
        static final String ROLE = "ROLE";
        static final String GRANT_REQUEST = "GRANT_REQUEST";
        static final String ADMIN = "ADMIN";
    }
}
