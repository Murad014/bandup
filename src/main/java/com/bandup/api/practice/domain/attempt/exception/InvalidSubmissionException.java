package com.bandup.api.practice.domain.attempt.exception;

import java.util.List;
import java.util.stream.Collectors;

import com.bandup.api.shared.domain.error.Violation;

/**
 * The submission itself is malformed (backend requirement 3.6): an unknown or
 * repeated question number, or a negative elapsed time. Carries every problem
 * found. The use case answers it with 400 (a bad request, not a broken
 * business rule, so it isn't a {@code DomainException}).
 */
public class InvalidSubmissionException extends RuntimeException {

    private final List<Violation> problems;

    public InvalidSubmissionException(List<Violation> problems) {
        super(problems.stream().map(Violation::message).collect(Collectors.joining(" ")));
        if (problems.isEmpty()) {
            throw new IllegalArgumentException("An invalid submission needs at least one problem");
        }
        this.problems = List.copyOf(problems);
    }

    public List<Violation> problems() {
        return problems;
    }
}
