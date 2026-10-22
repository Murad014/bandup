package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.bandup.api.practice.domain.attempt.exception.InvalidSubmissionException;
import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.Violation;

/**
 * The answers a learner submitted, by question number, as given. A number
 * can appear only once (enforced while building it). Whether the numbers
 * exist is checked against the exercise by {@code Attempt.grade}.
 */
@ValueObject
public final class AnswerSheet {

    private final Map<Integer, GivenAnswer> answers;

    private AnswerSheet(Map<Integer, GivenAnswer> answers) {
        this.answers = Collections.unmodifiableMap(answers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AnswerSheet empty() {
        return new AnswerSheet(new LinkedHashMap<>());
    }

    public Optional<GivenAnswer> answerTo(int number) {
        return Optional.ofNullable(answers.get(number));
    }

    /** Every answered number with its answer, in submission order. */
    public Map<Integer, GivenAnswer> asMap() {
        return answers;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnswerSheet s && answers.equals(s.answers);
    }

    @Override
    public int hashCode() {
        return answers.hashCode();
    }

    @Override
    public String toString() {
        return answers.toString();
    }

    /** Collects answers; {@link #build} fails if a number is repeated or below 1. */
    public static final class Builder {

        private final Map<Integer, GivenAnswer> answers = new LinkedHashMap<>();
        private final List<Integer> repeated = new ArrayList<>();
        private final List<Integer> invalid = new ArrayList<>();

        private Builder() {
        }

        public Builder answer(int number, GivenAnswer given) {
            Objects.requireNonNull(given, "given");
            if (number < 1) {
                invalid.add(number);
            } else if (answers.putIfAbsent(number, given) != null) {
                repeated.add(number);
            }
            return this;
        }

        /** @throws InvalidSubmissionException with every problem found */
        public AnswerSheet build() {
            List<Violation> problems = new ArrayList<>();
            invalid.forEach(n -> problems.add(new Violation("answers", "Question number " + n + " doesn't exist.")));
            repeated.stream().distinct().forEach(n -> problems.add(new Violation("answers", "Question " + n + " is answered more than once.")));
            if (!problems.isEmpty()) {
                throw new InvalidSubmissionException(problems);
            }
            return new AnswerSheet(new LinkedHashMap<>(answers));
        }
    }
}
