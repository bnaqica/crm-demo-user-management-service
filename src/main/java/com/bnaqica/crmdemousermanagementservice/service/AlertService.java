package com.bnaqica.crmdemousermanagementservice.service;

/** Raises an operational alert for admins. */
public interface AlertService {

    /** @param type short kebab-case alert type, such as {@code mass-lockout} */
    void raise(String type, String message);
}
