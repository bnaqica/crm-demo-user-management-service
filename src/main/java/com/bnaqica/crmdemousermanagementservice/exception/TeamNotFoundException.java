package com.bnaqica.crmdemousermanagementservice.exception;

import java.util.UUID;

/** No team with this id. Answered with 404. */
public class TeamNotFoundException extends RuntimeException {

    public TeamNotFoundException(UUID id) {
        super("Team " + id + " not found");
    }

    public TeamNotFoundException(UUID teamId, UUID sub) {
        super("User " + sub + " is not in team " + teamId);
    }
}
