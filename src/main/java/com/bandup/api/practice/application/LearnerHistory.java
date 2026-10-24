package com.bandup.api.practice.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.practice.application.port.AttemptHistory;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.application.UnauthenticatedException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.language.Translations;

/**
 * "My progress" (backend requirement 11): a signed-in learner's own attempts —
 * summary, list and review. Read-only; attempts are never re-graded.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearnerHistory {

    public static final int MAX_PAGE_SIZE = 50;

    private final AttemptHistory history;
    private final CurrentUser currentUser;
    private final LanguageCatalog languages;

    public record Summary(int attempts, int passagesDone, long totalSeconds, Integer averagePercent,
            Integer bestPercent, List<AttemptHistory.Passage> passages) {
    }

    public record Item(UUID attemptId, String passageId, String title, String coverUrl, List<String> topics,
            boolean available, Instant submittedAt, int score, int total, int percent, int elapsedSeconds) {
    }

    public record Page(List<Item> items, int page, int size, long totalItems) {
    }

    /**
     * @param updatedSince the exercise was revised after this attempt (questions may read differently now)
     * @param result       the same shape the learner saw after submitting
     */
    public record Review(String passageId, String title, boolean available, boolean updatedSince,
            Instant submittedAt, AttemptResult result) {
    }

    public Summary summary() {
        UUID user = userId();
        AttemptHistory.Totals totals = history.totals(user);
        return new Summary(totals.attempts(), totals.passagesDone(), totals.totalSeconds(), totals.averagePercent(),
                totals.bestPercent(), history.attemptedPassages(user));
    }

    /**
     * @param passageId only this passage's attempts (null or blank: all)
     * @throws InvalidRequestException (400) for a page below 0 or a size outside 1–50
     */
    public Page attempts(String passageId, int page, int size) {
        List<Violation> problems = new ArrayList<>();
        if (page < 0) {
            problems.add(new Violation("page", "The page must be 0 or more."));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            problems.add(new Violation("size", "The page size must be between 1 and " + MAX_PAGE_SIZE + "."));
        }
        if (!problems.isEmpty()) {
            throw new InvalidRequestException("The request is invalid.", problems);
        }
        UUID user = userId();
        String slug = passageId == null || passageId.isBlank() ? null : passageId.strip();
        List<Item> items = history.page(user, slug, page * size, size).stream()
                .map(i -> new Item(i.attemptId(), i.slug(), i.title(), i.coverUrl(), i.topics(), i.available(),
                        i.submittedAt(), i.score(), i.total(), percent(i.score(), i.total()), i.elapsedSeconds()))
                .toList();
        return new Page(items, page, size, history.count(user, slug));
    }

    /**
     * @param requestedLanguage help language for the explanations (may be null)
     * @throws NotFoundException (404) for an attempt that isn't the caller's, or doesn't exist
     */
    public Review review(UUID attemptId, String requestedLanguage) {
        AttemptHistory.Stored stored = history.find(userId(), attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt not found."));
        LanguageChain help = languages.fallbacks().chainFor(requestedLanguage);
        List<AttemptResult.QuestionResult> results = stored.answers().stream().map(a -> {
            boolean multi = "mcMulti".equals(a.family());
            Optional<Integer> last = multi && a.chooseCount() != null && a.chooseCount() > 1
                    ? Optional.of(a.number() + a.chooseCount() - 1) : Optional.empty();
            String answer = a.expected().isEmpty() ? "" : multi ? String.join(", ", a.expected()) : a.expected().getFirst();
            return new AttemptResult.QuestionResult(a.number(), last, a.points() == a.possible(), a.points(),
                    a.possible(), a.given(), answer, a.expected(), help.pick(Translations.of(a.explanations())),
                    Optional.ofNullable(a.evidenceKey()));
        }).toList();
        AttemptResult result = new AttemptResult(stored.attemptId(), stored.score(), stored.total(),
                percent(stored.score(), stored.total()), stored.elapsedSeconds(), results);
        return new Review(stored.slug(), stored.title(), stored.available(),
                stored.currentRevision() != stored.attemptRevision(), stored.submittedAt(), result);
    }

    private UUID userId() {
        return currentUser.get().map(caller -> caller.userId())
                .orElseThrow(() -> new UnauthenticatedException("Sign in to see your progress."));
    }

    /** Whole percentage, rounded half up — the same as {@code Attempt.percent()}. */
    static int percent(int score, int total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * score / total);
    }
}
