package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.List;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * One graded question of an attempt. {@code expected} is a snapshot of the
 * accepted answers at grading time, so later edits of the exercise never
 * change a learner's past result.
 *
 * @param number the first number the question showed (7 for "Q7–8")
 */
@ValueObject
public record GradedAnswer(QuestionId question, int number, GivenAnswer given, List<String> expected, Score score) {

    public GradedAnswer {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(given, "given");
        Objects.requireNonNull(score, "score");
        expected = List.copyOf(expected);
        if (number < 1) {
            throw new IllegalArgumentException("Invalid question number " + number);
        }
        if (expected.isEmpty()) {
            throw new IllegalArgumentException("Question " + number + " has no expected answer");
        }
    }

    public boolean isCorrect() {
        return score.isCorrect();
    }
}
