package com.bandup.api.practice.application;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.practice.application.port.GradableExercises;
import com.bandup.api.practice.domain.attempt.aggregate.Attempt;
import com.bandup.api.practice.domain.attempt.exception.InvalidSubmissionException;
import com.bandup.api.practice.domain.attempt.repository.AttemptRepository;
import com.bandup.api.practice.domain.attempt.service.Grader;
import com.bandup.api.practice.domain.attempt.valueobject.AnswerSheet;
import com.bandup.api.practice.domain.attempt.valueobject.GivenAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.GradableExercise;
import com.bandup.api.practice.domain.attempt.valueobject.GradableQuestion;
import com.bandup.api.practice.domain.attempt.valueobject.GradedAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.LearnerId;
import com.bandup.api.practice.domain.attempt.valueobject.QuestionFamily;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.numbering.NumberRange;

/**
 * Grades and stores a learner's attempt (backend requirement 3). The server
 * grades every question itself; nothing the client sends is trusted as a
 * grade. With a valid token the attempt is stored for that learner, without
 * one it's anonymous.
 */
@Service
public class SubmitAttempt {

    /**
     * @param slug              the exercise ({@code passageId} in the request)
     * @param elapsedSeconds    time spent; required, not negative
     * @param requestedLanguage help language for the explanations (may be null)
     */
    public record Command(String slug, Integer elapsedSeconds, List<Answer> answers, String requestedLanguage) {

        public Command {
            answers = answers == null ? List.of() : List.copyOf(answers);
        }
    }

    /**
     * One answer: {@code text} for a single value, {@code keys} for "Choose N";
     * both null means not answered.
     */
    public record Answer(Integer n, String text, List<String> keys) {
    }

    private final GradableExercises exercises;
    private final AttemptRepository attempts;
    private final CurrentUser currentUser;
    private final LanguageCatalog languages;
    private final Clock clock;
    private final Grader grader = new Grader();

    public SubmitAttempt(GradableExercises exercises, AttemptRepository attempts, CurrentUser currentUser,
            LanguageCatalog languages, Clock clock) {
        this.exercises = exercises;
        this.attempts = attempts;
        this.currentUser = currentUser;
        this.languages = languages;
        this.clock = clock;
    }

    /**
     * @throws InvalidRequestException (400) listing every problem: missing time,
     *         answer without a number, unknown or repeated number, a question
     *         answered twice, negative time
     * @throws NotFoundException (404) if the exercise doesn't exist or isn't PUBLISHED
     */
    @Transactional
    public AttemptResult submit(Command command) {
        List<Violation> problems = new ArrayList<>();
        AnswerSheet sheet = sheet(command, problems);
        GradableExercise exercise = exercises.findPublished(command.slug())
                .orElseThrow(() -> new NotFoundException("Passage not found."));
        LanguageChain help = languages.fallbacks().chainFor(command.requestedLanguage());
        Optional<LearnerId> learner = currentUser.get().map(caller -> new LearnerId(caller.userId()));
        Duration elapsed = Duration.ofSeconds(command.elapsedSeconds() == null ? 0 : command.elapsedSeconds());

        Attempt attempt = null;
        try {
            attempt = Attempt.grade(exercise, sheet, help.primary(), learner, elapsed, clock.instant(), grader);
        } catch (InvalidSubmissionException e) {
            problems.addAll(e.problems()); // unknown numbers, a question answered twice, negative time
        }
        if (!problems.isEmpty()) {
            throw new InvalidRequestException("The submission is invalid.", problems);
        }
        attempts.save(attempt);
        return result(attempt, exercise, help);
    }

    /**
     * The answer sheet built from the well-formed answers; problems with the
     * request's shape (missing time or number, number below 1, repeated
     * number) are added to {@code problems}, so the exercise checks can still
     * report theirs in the same response.
     */
    private static AnswerSheet sheet(Command command, List<Violation> problems) {
        if (command.elapsedSeconds() == null) {
            problems.add(new Violation("elapsedSeconds", "Elapsed time is required."));
        }
        AnswerSheet.Builder sheet = AnswerSheet.builder();
        Set<Integer> seen = new HashSet<>();
        Set<Integer> repeated = new LinkedHashSet<>();
        for (Answer answer : command.answers()) {
            if (answer == null || answer.n() == null) {
                problems.add(new Violation("answers", "Every answer needs a question number."));
            } else if (answer.n() < 1) {
                problems.add(new Violation("answers", "Question number " + answer.n() + " doesn't exist."));
            } else if (!seen.add(answer.n())) {
                repeated.add(answer.n());
            } else {
                sheet.answer(answer.n(), given(answer));
            }
        }
        repeated.forEach(n -> problems.add(new Violation("answers", "Question " + n + " is answered more than once.")));
        return sheet.build();
    }

    private static GivenAnswer given(Answer answer) {
        if (answer.keys() != null) {
            return new GivenAnswer.Keys(answer.keys());
        }
        return answer.text() == null ? GivenAnswer.blank() : new GivenAnswer.Text(answer.text());
    }

    private static AttemptResult result(Attempt attempt, GradableExercise exercise, LanguageChain help) {
        List<NumberRange> numbers = exercise.numbers();
        List<AttemptResult.QuestionResult> results = new ArrayList<>();
        for (int i = 0; i < attempt.answers().size(); i++) {
            GradedAnswer graded = attempt.answers().get(i);
            GradableQuestion question = exercise.questions().get(i);
            NumberRange range = numbers.get(i);
            List<String> accepted = graded.expected();
            String answer = question.rule().family() == QuestionFamily.MC_MULTI
                    ? String.join(", ", accepted) : accepted.getFirst();
            results.add(new AttemptResult.QuestionResult(graded.number(),
                    range.size() > 1 ? Optional.of(range.last()) : Optional.empty(),
                    graded.isCorrect(), graded.score().points(), graded.score().possible(),
                    shown(graded.given()), answer, accepted, help.pick(question.explanations()), question.evidence()));
        }
        return new AttemptResult(attempt.id().value(), attempt.score(), attempt.total(), attempt.percent(),
                Math.toIntExact(attempt.elapsed().toSeconds()), results);
    }

    private static Object shown(GivenAnswer given) {
        return switch (given) {
            case GivenAnswer.Text text -> text.value();
            case GivenAnswer.Keys keys -> keys.keys();
        };
    }
}
