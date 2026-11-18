package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/** @param grantedBy roles holding the permission, so admins can clean up deprecated ones */
@Builder
public record PermissionResponse(
        String key,
        String service,
        String realm,
        String description,
        boolean sensitive,
        boolean deprecated,
        Instant deprecatedAt,
        List<String> grantedBy) {}
