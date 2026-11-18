package com.bnaqica.crmdemousermanagementservice.dto.response;

import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/** @param permissions the role's effective permission keys */
@Builder
public record RoleResponse(
        String name,
        String description,
        String owner,
        UserType assignableTo,
        boolean builtIn,
        List<String> permissions,
        Instant createdAt,
        Instant updatedAt) {}
