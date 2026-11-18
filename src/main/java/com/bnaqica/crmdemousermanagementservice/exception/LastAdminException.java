package com.bnaqica.crmdemousermanagementservice.exception;

/** The change would leave crm-admin without an enabled admin. Answered with 409. */
public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("This is the last enabled admin account; enable or create another admin first");
    }
}
