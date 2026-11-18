package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.request.AssignRoleRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.TicketRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.RoleAssignmentResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.UserRolesResponse;
import com.bnaqica.crmdemousermanagementservice.service.RoleAdministrationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for the roles a user holds. */
@RestController
@RequestMapping("/admin/users/{sub}/roles")
@RequiredArgsConstructor
public class UserRoleAdminController {

    private final RoleAdministrationService roles;

    @GetMapping
    UserRolesResponse list(@PathVariable UUID sub) {
        return roles.rolesOf(sub);
    }

    /** 200 with the user's roles, or 202 with the grant request when a second admin must approve (Super Manager). */
    @PostMapping
    ResponseEntity<?> assign(@PathVariable UUID sub, @Valid @RequestBody AssignRoleRequest request) {
        RoleAssignmentResponse result = roles.assign(sub, request);
        return result.pending() == null
                ? ResponseEntity.ok(result.applied())
                : ResponseEntity.accepted().body(result.pending());
    }

    /** Carries the ticket in the body, like every other admin write. */
    @DeleteMapping("/{role}")
    UserRolesResponse revoke(
            @PathVariable UUID sub, @PathVariable String role, @Valid @RequestBody TicketRequest request) {
        return roles.revoke(sub, role, request.ticketRef());
    }
}
