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

    @ExceptionHandler(TeamNotFoundException.class)
    ProblemDetail handleTeamNotFound(TeamNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(TeamConflictException.class)
    ProblemDetail handleTeamConflict(TeamConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(RoleNotFoundException.class)
    ProblemDetail handleRoleNotFound(RoleNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(RoleConflictException.class)
    ProblemDetail handleRoleConflict(RoleConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(AuditSearchException.class)
    ProblemDetail handleAuditSearch(AuditSearchException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AuditRecordNotFoundException.class)
    ProblemDetail handleAuditRecordNotFound(AuditRecordNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PromotionConflictException.class)
    ProblemDetail handlePromotionConflict(PromotionConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ProblemDetail handleRateLimit(RateLimitExceededException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    @ExceptionHandler(LastAdminException.class)
    ProblemDetail handleLastAdmin(LastAdminException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(GrantRequestNotFoundException.class)
    ProblemDetail handleGrantRequestNotFound(GrantRequestNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(GrantRequestClosedException.class)
    ProblemDetail handleGrantRequestClosed(GrantRequestClosedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(CatalogConflictException.class)
    ProblemDetail handleCatalogConflict(CatalogConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
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
                HttpStatus.CONFLICT, "It was changed at the same time; reload it and try again");
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
