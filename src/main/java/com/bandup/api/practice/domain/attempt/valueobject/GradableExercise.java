package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.numbering.NumberRange;
import com.bandup.api.shared.domain.numbering.QuestionNumbering;

/**
 * Practice's own, read-only view of a PUBLISHED exercise: its questions in the
 * order the learner sees them, numbered by the shared
 * {@link QuestionNumbering} (a "Choose TWO" question takes two numbers).
 * Loaded through an application port; Content's classes are never used.
 */
@ValueObject
public record GradableExercise(ExerciseId id, ExerciseRevision revision, List<GradableQuestion> questions) {

    public GradableExercise {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(revision, "revision");
        questions = List.copyOf(questions);
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Exercise " + id.value() + " has no questions");
        }
        if (new HashSet<>(questions.stream().map(GradableQuestion::id).toList()).size() != questions.size()) {
            throw new IllegalArgumentException("Exercise " + id.value() + " lists a question twice");
        }
    }

    /** Each question's numbers, in question order. */
    public List<NumberRange> numbers() {
        return QuestionNumbering.number(questions.stream().map(q -> q.rule().points()).toList());
    }

    /** The question that shows {@code number} (any number of its range). */
    public Optional<GradableQuestion> questionNumbered(int number) {
        List<NumberRange> numbers = numbers();
        for (int i = 0; i < questions.size(); i++) {
            if (numbers.get(i).contains(number)) {
                return Optional.of(questions.get(i));
            }
        }
        return Optional.empty();
    }

    /** Points of the whole exercise (40 for a full IELTS test). */
    public int totalPoints() {
        return questions.stream().mapToInt(q -> q.rule().points()).sum();
    }
}
