package com.bnaqica.crmdemousermanagementservice.exception;

import java.util.UUID;

/** No grant request with this id. Answered with 404. */
public class GrantRequestNotFoundException extends RuntimeException {

    public GrantRequestNotFoundException(UUID id) {
        super("Grant request " + id + " not found");
    }
}
