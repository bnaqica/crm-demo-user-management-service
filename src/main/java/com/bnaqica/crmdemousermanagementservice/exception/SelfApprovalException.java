package com.bnaqica.crmdemousermanagementservice.exception;

import org.springframework.security.access.AccessDeniedException;

/** An admin tried to approve their own grant request. Spring Security answers it with 403. */
public class SelfApprovalException extends AccessDeniedException {

    public SelfApprovalException() {
        super("A grant request must be approved by a different admin");
    }
}
