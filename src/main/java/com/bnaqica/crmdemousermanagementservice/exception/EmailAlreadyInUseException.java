package com.bnaqica.crmdemousermanagementservice.exception;

/** The email already belongs to an account in crm-internal or portal. Answered with 409. */
public class EmailAlreadyInUseException extends RuntimeException {

    public EmailAlreadyInUseException() {
        super("An account with this email already exists");
    }
}
