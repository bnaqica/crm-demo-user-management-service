package com.bnaqica.crmdemousermanagementservice.exception;

/** A message on {@code keycloak-events} can't be read. It goes to the dead-letter topic without retries. */
public class InvalidKeycloakEventException extends RuntimeException {

    public InvalidKeycloakEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
