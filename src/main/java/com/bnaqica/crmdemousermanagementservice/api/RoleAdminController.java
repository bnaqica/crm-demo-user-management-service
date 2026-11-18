package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.request.CreateRoleRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.UpdateRoleRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.RoleResponse;
import com.bnaqica.crmdemousermanagementservice.service.RoleAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for roles, addressed by name (also their Keycloak name). Roles are not deleted. */
@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
public class RoleAdminController {

    private final RoleAdministrationService roles;

    @GetMapping
    List<RoleResponse> list() {
        return roles.list();
    }

    @GetMapping("/{name}")
    RoleResponse get(@PathVariable String name) {
        return roles.get(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoleResponse create(@Valid @RequestBody CreateRoleRequest request) {
        return roles.create(request);
    }

    @PutMapping("/{name}")
    RoleResponse update(@PathVariable String name, @Valid @RequestBody UpdateRoleRequest request) {
        return roles.update(name, request);
    }
}
