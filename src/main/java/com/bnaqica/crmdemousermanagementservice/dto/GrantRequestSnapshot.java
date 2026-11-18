package com.bnaqica.crmdemousermanagementservice.dto;

import com.bnaqica.crmdemousermanagementservice.entity.GrantRequest;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestStatus;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestType;

/** A grant request's audited fields: both admins are in it once decided. */
public record GrantRequestSnapshot(
        GrantRequestType type, String target, GrantRequestStatus status, String requestedBy, String decidedBy) {

    public static GrantRequestSnapshot of(GrantRequest request) {
        return new GrantRequestSnapshot(
                request.getType(),
                request.getTarget(),
                request.getStatus(),
                request.getRequestedBy(),
                request.getDecidedBy());
    }
}
