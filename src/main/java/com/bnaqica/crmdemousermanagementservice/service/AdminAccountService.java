package com.bnaqica.crmdemousermanagementservice.service;

import java.util.UUID;

/** Admin accounts in crm-admin. Creating one goes through {@link PrivilegedGrantService}. */
public interface AdminAccountService {

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.LastAdminException if it is the last enabled admin */
    void disable(UUID id, String ticketRef);

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.LastAdminException if it is the last enabled admin */
    void delete(UUID id, String ticketRef);
}
