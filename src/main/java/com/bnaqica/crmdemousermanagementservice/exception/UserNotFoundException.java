package com.bnaqica.crmdemousermanagementservice.exception;

import java.util.UUID;

/** No user with this sub is managed by the UMS. Answered with 404. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID sub) {
        super("User " + sub + " not found");
    }
}
