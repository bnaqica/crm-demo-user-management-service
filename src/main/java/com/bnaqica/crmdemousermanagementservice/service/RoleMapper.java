package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.response.RoleResponse;
import com.bnaqica.crmdemousermanagementservice.entity.Role;

/** Maps roles to API responses. */
final class RoleMapper {

    private RoleMapper() {}

    static RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .name(role.getName())
                .description(role.getDescription())
                .owner(role.getOwner())
                .assignableTo(role.getAssignableTo())
                .builtIn(role.isBuiltIn())
                .permissions(RoleRules.keys(role).stream().sorted().toList())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
