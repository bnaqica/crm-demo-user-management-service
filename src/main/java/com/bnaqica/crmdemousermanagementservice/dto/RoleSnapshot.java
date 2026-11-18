package com.bnaqica.crmdemousermanagementservice.dto;

import com.bnaqica.crmdemousermanagementservice.entity.Role;
import java.util.List;

/** A role's audited fields, for the before and after of an audit event. */
public record RoleSnapshot(String description, String owner, List<String> permissions) {

    public static RoleSnapshot of(Role role, List<String> permissions) {
        return new RoleSnapshot(role.getDescription(), role.getOwner(), permissions);
    }
}
