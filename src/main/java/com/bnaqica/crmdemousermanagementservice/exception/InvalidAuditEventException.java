package com.bnaqica.crmdemousermanagementservice.exception;

/** An audit or Keycloak event that can't be stored: unreadable, or missing a required field. Never retried. */
public class InvalidAuditEventException extends RuntimeException {

    public InvalidAuditEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
