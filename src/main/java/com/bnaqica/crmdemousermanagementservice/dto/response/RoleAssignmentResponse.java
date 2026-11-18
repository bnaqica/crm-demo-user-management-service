package com.bnaqica.crmdemousermanagementservice.dto.response;

/** Either the user's roles after the assignment, or the grant request waiting for a second admin (Super Manager). */
public record RoleAssignmentResponse(UserRolesResponse applied, GrantRequestResponse pending) {

    public static RoleAssignmentResponse applied(UserRolesResponse roles) {
        return new RoleAssignmentResponse(roles, null);
    }

    public static RoleAssignmentResponse pending(GrantRequestResponse request) {
        return new RoleAssignmentResponse(null, request);
    }
}
