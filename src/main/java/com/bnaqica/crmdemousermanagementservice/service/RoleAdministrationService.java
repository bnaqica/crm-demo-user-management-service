package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.request.AssignRoleRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.CreateRoleRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.UpdateRoleRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.RoleAssignmentResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.RoleResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserRolesResponse;
import java.util.List;
import java.util.UUID;

/** Admin work on roles: definitions, and assigning them to users. Every write is audited under a ticket. */
public interface RoleAdministrationService {

    List<RoleResponse> list();

    RoleResponse get(String name);

    RoleResponse create(CreateRoleRequest request);

    RoleResponse update(String name, UpdateRoleRequest request);

    UserRolesResponse rolesOf(UUID sub);

    /** Applies the assignment, or for Super Manager opens a grant request for a second admin. */
    RoleAssignmentResponse assign(UUID sub, AssignRoleRequest request);

    UserRolesResponse revoke(UUID sub, String role, String ticketRef);
}
