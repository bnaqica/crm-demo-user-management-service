package com.bnaqica.crmdemousermanagementservice.exception;

/** An audit search without a valid, bounded time range. Answered with 400. */
public class AuditSearchException extends RuntimeException {

    public AuditSearchException(String message) {
        super(message);
    }
}
