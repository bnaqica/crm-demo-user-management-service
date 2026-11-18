package com.bnaqica.crmdemousermanagementservice.dto.response;

import java.util.List;

/** A page of audit records, newest first. */
public record AuditPageResponse(
        List<AuditRecordResponse> content, int page, int size, long totalElements, int totalPages) {}
