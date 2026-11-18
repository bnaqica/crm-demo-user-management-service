package com.bnaqica.crmdemousermanagementservice.exception;

/** A role change clashes with the current state, such as a taken name or a built-in role. Answered with 409. */
public class RoleConflictException extends RuntimeException {

    public RoleConflictException(String message) {
        super(message);
    }
}
