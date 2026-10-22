package com.bandup.api.practice.domain.attempt.aggregate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.bandup.api.practice.domain.attempt.exception.InvalidSubmissionException;
import com.bandup.api.practice.domain.attempt.service.Grader;
import com.bandup.api.practice.domain.attempt.valueobject.AnswerSheet;
import com.bandup.api.practice.domain.attempt.valueobject.AttemptId;
import com.bandup.api.practice.domain.attempt.valueobject.ExerciseId;
import com.bandup.api.practice.domain.attempt.valueobject.ExerciseRevision;
import com.bandup.api.practice.domain.attempt.valueobject.GivenAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.GradableExercise;
import com.bandup.api.practice.domain.attempt.valueobject.GradableQuestion;
import com.bandup.api.practice.domain.attempt.valueobject.GradedAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.LearnerId;
import com.bandup.api.practice.domain.attempt.valueobject.QuestionId;
import com.bandup.api.shared.domain.ddd.AggregateRoot;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.numbering.NumberRange;

/**
 * One graded submission of an exercise (aggregate root; backend requirement 3).
 * Immutable: once graded, an attempt is history and never changes, even if
 * the exercise is edited later (it keeps the revision and an answer snapshot).
 *
 * <p>Every question of the exercise gets a {@link GradedAnswer}, in number
 * order; an unanswered question scores 0. Score, total and percent are
 * derived from the answers, so they can't disagree with them. References the
 * exercise and the learner only by id.
 */
@AggregateRoot
public final class Attempt {

    private final AttemptId id;
    private final ExerciseId exercise;
    private final ExerciseRevision revision;
    private final LearnerId learner;
    private final LanguageCode language;
    private final Duration elapsed;
    private final Instant submittedAt;
    private final List<GradedAnswer> answers;

    private Attempt(AttemptId id, ExerciseId exercise, ExerciseRevision revision, LearnerId learner,
            LanguageCode language, Duration elapsed, Instant submittedAt, List<GradedAnswer> answers) {
        this.id = Objects.requireNonNull(id, "id");
        this.exercise = Objects.requireNonNull(exercise, "exercise");
        this.revision = Objects.requireNonNull(revision, "revision");
        this.learner = learner;
        this.language = Objects.requireNonNull(language, "language");
        this.elapsed = Objects.requireNonNull(elapsed, "elapsed");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.answers = List.copyOf(answers);
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Attempt " + id + " has a negative elapsed time");
        }
        if (this.answers.isEmpty()) {
            throw new IllegalArgumentException("Attempt " + id + " has no answers");
        }
        if (new HashSet<>(this.answers.stream().map(GradedAnswer::question).toList()).size() != this.answers.size()) {
            throw new IllegalArgumentException("Attempt " + id + " grades a question twice");
        }
    }

    /**
     * Grades every question of {@code exercise} against {@code sheet}.
     *
     * @param learner the signed-in learner, or empty for an anonymous attempt
     * @throws InvalidSubmissionException if a number is unknown, two numbers
     *         answer the same question ("Q7" and "Q8" of one "Choose TWO"),
     *         or {@code elapsed} is negative; with every problem found
     */
    public static Attempt grade(GradableExercise exercise, AnswerSheet sheet, LanguageCode language,
            Optional<LearnerId> learner, Duration elapsed, Instant now, Grader grader) {
        List<Violation> problems = new ArrayList<>();
        if (elapsed.isNegative()) {
            problems.add(new Violation("elapsedSeconds", "Elapsed time can't be negative."));
        }
        Map<QuestionId, GivenAnswer> givenByQuestion = new HashMap<>();
        sheet.asMap().forEach((number, given) -> exercise.questionNumbered(number).ifPresentOrElse(
                question -> {
                    if (givenByQuestion.putIfAbsent(question.id(), given) != null) {
                        problems.add(new Violation("answers", "Question " + number + " is answered more than once."));
                    }
                },
                () -> problems.add(new Violation("answers", "Question number " + number + " doesn't exist."))));
        if (!problems.isEmpty()) {
            throw new InvalidSubmissionException(problems);
        }

        List<NumberRange> numbers = exercise.numbers();
        List<GradedAnswer> graded = new ArrayList<>();
        for (int i = 0; i < exercise.questions().size(); i++) {
            GradableQuestion question = exercise.questions().get(i);
            GivenAnswer given = givenByQuestion.getOrDefault(question.id(), GivenAnswer.blank());
            graded.add(new GradedAnswer(question.id(), numbers.get(i).first(), given,
                    question.rule().accepted(), grader.score(question.rule(), given)));
        }
        return new Attempt(AttemptId.newId(), exercise.id(), exercise.revision(), learner.orElse(null),
                language, elapsed, now, graded);
    }

    /** Rebuilds a stored attempt (for the persistence adapter). */
    public static Attempt restore(AttemptId id, ExerciseId exercise, ExerciseRevision revision,
            Optional<LearnerId> learner, LanguageCode language, Duration elapsed, Instant submittedAt,
            List<GradedAnswer> answers) {
        return new Attempt(id, exercise, revision, learner.orElse(null), language, elapsed, submittedAt, answers);
    }

    public AttemptId id() {
        return id;
    }

    public ExerciseId exercise() {
        return exercise;
    }

    public ExerciseRevision revision() {
        return revision;
    }

    /** Empty for an anonymous attempt. */
    public Optional<LearnerId> learner() {
        return Optional.ofNullable(learner);
    }

    /** The learner's support language (for explanations in the result). */
    public LanguageCode language() {
        return language;
    }

    public Duration elapsed() {
        return elapsed;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    /** One graded answer per question, in number order. */
    public List<GradedAnswer> answers() {
        return answers;
    }

    public int score() {
        return answers.stream().mapToInt(a -> a.score().points()).sum();
    }

    public int total() {
        return answers.stream().mapToInt(a -> a.score().possible()).sum();
    }

    /** Score as a whole percentage, rounded half up (25 of 40 → 63). */
    public int percent() {
        return (int) Math.round(100.0 * score() / total());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Attempt a && id.equals(a.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Attempt[" + id + ", exercise " + exercise.value() + ", " + score() + "/" + total() + "]";
    }
}
