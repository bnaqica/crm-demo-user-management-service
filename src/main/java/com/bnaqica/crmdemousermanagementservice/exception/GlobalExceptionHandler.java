package com.bnaqica.crmdemousermanagementservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns exceptions into {@link ProblemDetail} responses. Add a handler here for each custom exception. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleNotFound(UserNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    ProblemDetail handleEmailInUse(EmailAlreadyInUseException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ProblemDetail handleStatusTransition(InvalidStatusTransitionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidUserRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidUserRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    /** Someone else changed the account between our read and our write. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentChange(OptimisticLockingFailureException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The account was changed at the same time; reload it and try again");
    }

    /** An unknown {@code sort} property on a search. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleBadProperty(PropertyReferenceException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** Keycloak failed for a reason the caller can't fix (a duplicate is translated to 409 before it gets here). */
    @ExceptionHandler(KeycloakClientException.class)
    ProblemDetail handleKeycloak(KeycloakClientException exception) {
        log.error("Keycloak call failed with status {}", exception.getStatus(), exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The identity provider call failed");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) throws Exception {
        // Spring Security has to see these to answer 401 or 403
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
            throw exception;
        }
        log.error("Unhandled exception", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
