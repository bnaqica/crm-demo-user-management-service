package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.entity.Permission;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import java.util.Set;
import java.util.stream.Collectors;

/** Rules about what a role holds. */
final class RoleRules {

    private RoleRules() {}

    static Set<String> keys(Role role) {
        return role.getPermissions().stream().map(Permission::getKey).collect(Collectors.toSet());
    }
}
