package com.bnaqica.crmdemousermanagementservice.service;

/** Stores events from {@code audit-events} and {@code keycloak-events} in the central audit database. */
public interface AuditIngestionService {

    /**
     * An event in the common audit schema, from the UMS, CRM services or the portal.
     *
     * @throws com.bnaqica.crmdemousermanagementservice.exception.InvalidAuditEventException if it can't be read or lacks eventId, occurredAt, source or action
     */
    void ingestAuditEvent(String payload);

    /** A Keycloak user event (login and the like) or admin event ({@code "kind": "ADMIN"}). */
    void ingestKeycloakEvent(String payload);
}
