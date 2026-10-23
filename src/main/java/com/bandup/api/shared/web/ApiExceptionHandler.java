package com.bandup.api.shared.web;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.ForbiddenException;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.application.TooManyRequestsException;
import com.bandup.api.shared.application.UnauthenticatedException;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * Turns exceptions into Problem Details responses (requirement 7). Spring's own
 * MVC errors (malformed JSON, wrong parameter types, unknown URLs, wrong
 * method, ...) are already Problem Details via {@link ResponseEntityExceptionHandler}.
 *
 * <p>Global on purpose: unknown URLs aren't handled by any of our controllers,
 * so a handler limited to {@code com.bandup.api} would never see them.
 * Internal details (SQL, stack traces) are logged, never returned.
 */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail ruleViolated(DomainException e) {
        return ApiProblems.validation(e.violations());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException e) {
        return ApiProblems.badRequest(e.getMessage(), e.errors());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ApiProblems.of(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return ApiProblems.of(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ProblemDetail unauthenticated(UnauthenticatedException e) {
        return ApiProblems.unauthenticated(e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> tooMany(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, e.retryAfter().toSeconds())))
                .body(ApiProblems.of(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts", e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException e) {
        return ApiProblems.forbidden(e.getMessage());
    }

    /** Missing, invalid or expired token (also reached from the security filters). */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail notSignedIn(AuthenticationException e) {
        return ApiProblems.unauthenticated("Sign in to continue.");
    }

    /** Valid token without the required role (also reached from the security filters). */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail notAllowed(AccessDeniedException e) {
        return ApiProblems.forbidden("You don't have permission to do this.");
    }

    /** A database rule caught something the application didn't; never expose the SQL. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataConflict(DataIntegrityViolationException e) {
        log.warn("Database constraint violated: {}", e.getMostSpecificCause().getMessage());
        return ApiProblems.of(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ApiProblems.of(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "An unexpected error occurred. Please try again later.");
    }
}
