package com.bandup.api.shared.web;

import java.util.List;

import lombok.experimental.UtilityClass;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.bandup.api.shared.domain.error.Violation;

/**
 * Builds the API's error bodies (Problem Details, RFC 9457) so every error looks
 * the same, whether it comes from a controller or, later, from the security
 * filters (401/403).
 */
@UtilityClass
public final class ApiProblems {

    public static ProblemDetail of(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    /** 422 with one {@code { field, message }} entry per broken rule. */
    public static ProblemDetail validation(List<Violation> violations) {
        ProblemDetail problem = of(HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed",
                "One or more fields are invalid.");
        problem.setProperty("errors", violations.stream().map(v -> new FieldError(v.field(), v.message())).toList());
        return problem;
    }

    /** 400; with an {@code errors} list (same shape as a 422) when there are field problems. */
    public static ProblemDetail badRequest(String detail, List<Violation> errors) {
        ProblemDetail problem = of(HttpStatus.BAD_REQUEST, "Invalid request", detail);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors.stream().map(v -> new FieldError(v.field(), v.message())).toList());
        }
        return problem;
    }

    public static ProblemDetail unauthenticated(String detail) {
        return of(HttpStatus.UNAUTHORIZED, "Not signed in", detail);
    }

    public static ProblemDetail forbidden(String detail) {
        return of(HttpStatus.FORBIDDEN, "Not allowed", detail);
    }

    /** One entry of the 422 {@code errors} list, as the admin UI expects it. */
    public record FieldError(String field, String message) {
    }
}
