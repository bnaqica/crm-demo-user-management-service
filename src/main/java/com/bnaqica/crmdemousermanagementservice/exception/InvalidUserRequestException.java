package com.bnaqica.crmdemousermanagementservice.exception;

/** The request is well-formed but breaks a business rule, such as a role the user type can't hold. Answered with 422. */
public class InvalidUserRequestException extends RuntimeException {

    public InvalidUserRequestException(String message) {
        super(message);
    }
}
