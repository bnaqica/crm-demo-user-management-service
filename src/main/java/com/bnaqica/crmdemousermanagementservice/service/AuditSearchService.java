package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.AuditSearchCriteria;
import com.bnaqica.crmdemousermanagementservice.dto.response.AuditPageResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.AuditRecordResponse;
import java.util.UUID;

/** Read-only search over the central audit store, for admins. */
public interface AuditSearchService {

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.AuditSearchException without a range, or with one longer than the maximum */
    AuditPageResponse search(AuditSearchCriteria criteria, int page, int size);

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.AuditRecordNotFoundException if there is none */
    AuditRecordResponse get(UUID eventId);
}
