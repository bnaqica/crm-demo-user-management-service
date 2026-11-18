package com.bnaqica.crmdemousermanagementservice.exception;

/** No role with this name. Answered with 404. */
public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(String name) {
        super("Role " + name + " not found");
    }

    public RoleNotFoundException(String name, java.util.UUID sub) {
        super("User " + sub + " doesn't hold role " + name);
    }
}
