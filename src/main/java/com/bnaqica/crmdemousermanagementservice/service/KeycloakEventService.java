package com.bnaqica.crmdemousermanagementservice.service;

/** Reacts to Keycloak login and admin events. */
public interface KeycloakEventService {

    /**
     * @param payload the event JSON from {@code keycloak-events}
     * @throws com.bnaqica.crmdemousermanagementservice.exception.InvalidKeycloakEventException if it can't be read
     */
    void handle(String payload);
}
