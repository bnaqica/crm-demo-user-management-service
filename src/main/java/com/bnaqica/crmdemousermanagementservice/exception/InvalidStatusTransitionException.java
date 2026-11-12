package com.bnaqica.crmdemousermanagementservice.exception;

import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;

/** The account can't move from its current status to the requested one. Answered with 409. */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(UserStatus from, UserStatus to) {
        super("A " + from + " account can't become " + to);
    }
}
