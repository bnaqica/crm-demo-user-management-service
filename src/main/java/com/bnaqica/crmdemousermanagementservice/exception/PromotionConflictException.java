package com.bnaqica.crmdemousermanagementservice.exception;

/** The user can't be promoted: internal, disabled, or holding neither portal role. Answered with 409. */
public class PromotionConflictException extends RuntimeException {

    public PromotionConflictException(String message) {
        super(message);
    }
}
