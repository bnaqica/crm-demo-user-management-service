package com.bnaqica.crmdemousermanagementservice.exception;

/** A team change clashes with the current state: a taken name, an archived team, a user already in it. Answered with 409. */
public class TeamConflictException extends RuntimeException {

    public TeamConflictException(String message) {
        super(message);
    }
}
