package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.ApiAuditEntry;

public interface ApiAuditService {

    void record(ApiAuditEntry entry);
}
