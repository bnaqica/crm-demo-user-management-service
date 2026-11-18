package com.bnaqica.crmdemousermanagementservice.exception;

/** The grant request was already approved, rejected or expired. Answered with 409. */
public class GrantRequestClosedException extends RuntimeException {

    public GrantRequestClosedException(String message) {
        super(message);
    }
}
