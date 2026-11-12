package com.bnaqica.crmdemousermanagementservice.exception;

import lombok.Getter;

/** A Keycloak Admin REST API call failed. {@code status} is Keycloak's HTTP status, or 0 if Keycloak couldn't be reached. */
@Getter
public class KeycloakClientException extends RuntimeException {

    private final int status;

    public KeycloakClientException(int status, String message) {
        super(message);
        this.status = status;
    }

    public KeycloakClientException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public boolean isNotFound() {
        return status == 404;
    }

    public boolean isConflict() {
        return status == 409;
    }
}
