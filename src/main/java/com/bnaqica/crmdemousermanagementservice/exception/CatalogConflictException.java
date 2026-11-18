package com.bnaqica.crmdemousermanagementservice.exception;

/** A permission clashes with the catalog, such as a key another service already registered. Answered with 409. */
public class CatalogConflictException extends RuntimeException {

    public CatalogConflictException(String message) {
        super(message);
    }
}
