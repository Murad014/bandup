package com.bandup.api.shared.domain.error;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;

/**
 * A business rule was broken. Carries every {@link Violation} found, so a
 * caller can report all problems at once instead of one at a time.
 */
public class DomainException extends RuntimeException {

    @Getter
    private final List<Violation> violations;

    public DomainException(List<Violation> violations) {
        super(describe(violations));
        this.violations = List.copyOf(violations);
    }

    public DomainException(Violation violation) {
        this(List.of(violation));
    }

    public static DomainException of(String field, String message) {
        return new DomainException(new Violation(field, message));
    }

    private static String describe(List<Violation> violations) {
        if (violations == null || violations.isEmpty()) {
            throw new IllegalArgumentException("A domain exception needs at least one violation");
        }
        return violations.stream()
                .map(v -> v.field().isEmpty() ? v.message() : v.field() + ": " + v.message())
                .collect(Collectors.joining("; "));
    }
}
