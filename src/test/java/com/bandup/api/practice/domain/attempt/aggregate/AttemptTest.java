package com.bandup.api.practice.domain.attempt.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bandup.api.practice.domain.attempt.exception.InvalidSubmissionException;
import com.bandup.api.practice.domain.attempt.service.Grader;
import com.bandup.api.practice.domain.attempt.valueobject.AnswerSheet;
import com.bandup.api.practice.domain.attempt.valueobject.ExerciseId;
import com.bandup.api.practice.domain.attempt.valueobject.ExerciseRevision;
import com.bandup.api.practice.domain.attempt.valueobject.GivenAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.GradableExercise;
import com.bandup.api.practice.domain.attempt.valueobject.GradableQuestion;
import com.bandup.api.practice.domain.attempt.valueobject.GradedAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.GradingRule;
import com.bandup.api.practice.domain.attempt.valueobject.LearnerId;
import com.bandup.api.practice.domain.attempt.valueobject.QuestionId;
import com.bandup.api.practice.domain.attempt.valueobject.WordLimit;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

class AttemptTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");
    private static final LanguageCode AZ = LanguageCode.of("az");
    private static final Grader GRADER = new Grader();

    /** Q1 TFNG, Q2 gap (two words), Q3–4 "Choose TWO", Q5 heading match. Total 5 points. */
    private static final GradableExercise WOLVES = new GradableExercise(new ExerciseId(7), new ExerciseRevision(3), List.of(
            question(11, GradingRule.choiceFixed("FALSE"), "e1"),
            question(12, GradingRule.gap(List.of("riverbanks", "river banks"), new WordLimit(2, false)), "e2"),
            question(13, GradingRule.mcMulti(List.of("A", "D"), 2), null),
            question(14, GradingRule.match("iv"), "e4")));

    @Test
    void gradesEveryQuestionInNumberOrder() {
        Attempt attempt = grade(sheet().answer(1, text("false")).answer(2, text("River banks"))
                .answer(3, keys("A", "D")).answer(5, text("iv")));

        assertThat(attempt.answers()).extracting(GradedAnswer::number).containsExactly(1, 2, 3, 5);
        assertThat(attempt.answers()).allMatch(GradedAnswer::isCorrect);
        assertThat(attempt.score()).isEqualTo(5);
        assertThat(attempt.total()).isEqualTo(5);
        assertThat(attempt.percent()).isEqualTo(100);
    }

    @Test
    void mixedAttemptWithPartialChooseTwoAndBlankQuestion() {
        Attempt attempt = grade(sheet().answer(1, text("TRUE")).answer(4, keys("A", "C")).answer(5, text("iv")));

        assertThat(attempt.answers()).extracting(a -> a.score().points()).containsExactly(0, 0, 1, 1);
        assertThat(attempt.answers().get(1).given()).isEqualTo(GivenAnswer.blank()); // Q2 not answered
        assertThat(attempt.score()).isEqualTo(2);
        assertThat(attempt.total()).isEqualTo(5);
        assertThat(attempt.percent()).isEqualTo(40);
    }

    @Test
    void keepsTheRevisionTheGivenValuesAndASnapshotOfTheExpectedAnswers() {
        Attempt attempt = grade(sheet().answer(2, text("  rivers ")));

        assertThat(attempt.exercise()).isEqualTo(new ExerciseId(7));
        assertThat(attempt.revision()).isEqualTo(new ExerciseRevision(3));
        GradedAnswer q2 = attempt.answers().get(1);
        assertThat(q2.question()).isEqualTo(new QuestionId(12));
        assertThat(q2.given()).isEqualTo(text("  rivers ")); // stored as given, not normalized
        assertThat(q2.expected()).containsExactly("riverbanks", "river banks");
    }

    @Test
    void percentIsRoundedHalfUp() {
        // 1 of 8 = 12.5% → 13
        GradableExercise eight = new GradableExercise(new ExerciseId(1), new ExerciseRevision(1), List.of(
                question(1, GradingRule.mcMulti(List.of("A", "B", "C", "D"), 4), null),
                question(2, GradingRule.mcMulti(List.of("A", "B", "C", "D"), 4), null)));
        Attempt attempt = Attempt.grade(eight, sheet().answer(1, keys("A")).build(), AZ, Optional.empty(),
                Duration.ZERO, NOW, GRADER);

        assertThat(attempt.percent()).isEqualTo(13);
    }

    @Test
    void anyNumberOfAChooseTwoQuestionIdentifiesIt() {
        assertThat(grade(sheet().answer(4, keys("A", "D"))).answers().get(2).score().points()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownNumbersAndTwoAnswersToOneQuestionAndNegativeTimeAllAtOnce() {
        AnswerSheet bad = sheet().answer(3, keys("A")).answer(4, keys("D")).answer(6, text("x")).build();

        assertThatThrownBy(() -> Attempt.grade(WOLVES, bad, AZ, Optional.empty(), Duration.ofSeconds(-1), NOW, GRADER))
                .isInstanceOfSatisfying(InvalidSubmissionException.class, e -> assertThat(e.problems())
                        .extracting(Violation::message)
                        .containsExactlyInAnyOrder(
                                "Elapsed time can't be negative.",
                                "Question 4 is answered more than once.",
                                "Question number 6 doesn't exist."));
    }

    @Test
    void theSheetRejectsRepeatedAndInvalidNumbers() {
        assertThatThrownBy(() -> sheet().answer(1, text("TRUE")).answer(1, text("FALSE")).answer(0, text("x")).build())
                .isInstanceOfSatisfying(InvalidSubmissionException.class, e -> assertThat(e.problems())
                        .extracting(Violation::message)
                        .containsExactlyInAnyOrder("Question 1 is answered more than once.", "Question number 0 doesn't exist."));
    }

    @Test
    void recordsTheLearnerOrIsAnonymous() {
        LearnerId aysel = new LearnerId(UUID.randomUUID());
        Attempt signedIn = Attempt.grade(WOLVES, AnswerSheet.empty(), AZ, Optional.of(aysel), Duration.ofSeconds(95), NOW, GRADER);
        Attempt anonymous = grade(sheet());

        assertThat(signedIn.learner()).contains(aysel);
        assertThat(signedIn.elapsed()).isEqualTo(Duration.ofSeconds(95));
        assertThat(signedIn.submittedAt()).isEqualTo(NOW);
        assertThat(signedIn.language()).isEqualTo(AZ);
        assertThat(anonymous.learner()).isEmpty();
        assertThat(anonymous.score()).isZero();
    }

    @Test
    void isEqualByIdAndRestoresWithItsInvariants() {
        Attempt attempt = grade(sheet().answer(1, text("FALSE")));
        Attempt restored = Attempt.restore(attempt.id(), attempt.exercise(), attempt.revision(), attempt.learner(),
                attempt.language(), attempt.elapsed(), attempt.submittedAt(), attempt.answers());

        assertThat(restored).isEqualTo(attempt).hasSameHashCodeAs(attempt);
        assertThat(restored.score()).isEqualTo(attempt.score());
        assertThat(grade(sheet().answer(1, text("FALSE")))).isNotEqualTo(attempt);

        assertThatThrownBy(() -> Attempt.restore(attempt.id(), attempt.exercise(), attempt.revision(), Optional.empty(),
                AZ, Duration.ZERO, NOW, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Attempt.restore(attempt.id(), attempt.exercise(), attempt.revision(), Optional.empty(),
                AZ, Duration.ZERO, NOW, List.of(attempt.answers().getFirst(), attempt.answers().getFirst())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anExerciseNeedsDistinctQuestions() {
        GradableQuestion q = question(1, GradingRule.match("A"), null);
        assertThatThrownBy(() -> new GradableExercise(new ExerciseId(1), new ExerciseRevision(1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GradableExercise(new ExerciseId(1), new ExerciseRevision(1), List.of(q, q)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(WOLVES.totalPoints()).isEqualTo(5);
        assertThat(WOLVES.questionNumbered(6)).isEmpty();
    }

    private static Attempt grade(AnswerSheet.Builder sheet) {
        return Attempt.grade(WOLVES, sheet.build(), AZ, Optional.empty(), Duration.ofMinutes(12), NOW, GRADER);
    }

    private static AnswerSheet.Builder sheet() {
        return AnswerSheet.builder();
    }

    private static GivenAnswer text(String value) {
        return new GivenAnswer.Text(value);
    }

    private static GivenAnswer keys(String... keys) {
        return new GivenAnswer.Keys(List.of(keys));
    }

    private static GradableQuestion question(long id, GradingRule rule, String evidence) {
        return new GradableQuestion(new QuestionId(id), rule, evidence, Translations.empty());
    }
}
