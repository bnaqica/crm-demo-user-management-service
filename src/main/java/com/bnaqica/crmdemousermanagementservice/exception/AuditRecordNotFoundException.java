package com.bnaqica.crmdemousermanagementservice.exception;

import java.util.UUID;

/** No audit record with this event id. Answered with 404. */
public class AuditRecordNotFoundException extends RuntimeException {

    public AuditRecordNotFoundException(UUID eventId) {
        super("Audit record " + eventId + " not found");
    }
}
