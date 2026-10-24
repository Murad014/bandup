package com.bandup.api.practice.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.bandup.api.shared.domain.language.LanguageCode;

/** Port: a learner's stored attempts, read-only ("My progress", design §7.5). */
public interface AttemptHistory {

    Totals totals(UUID learner);

    /** The passages the learner has attempted, newest attempt first (for the filter). */
    List<Passage> attemptedPassages(UUID learner);

    /** @param slug only this passage's attempts, or null for all */
    long count(UUID learner, String slug);

    /** Newest first. */
    List<Item> page(UUID learner, String slug, int offset, int limit);

    /** The learner's own attempt with every answer; empty for anyone else's or an unknown id. */
    Optional<Stored> find(UUID learner, UUID attemptId);

    /**
     * @param averagePercent mean of the attempts' percents (null without attempts)
     * @param bestPercent    the highest percent (null without attempts)
     */
    record Totals(int attempts, int passagesDone, long totalSeconds, Integer averagePercent, Integer bestPercent) {
    }

    record Passage(String slug, String title) {
    }

    /** @param available the exercise is still published (it can be opened again) */
    record Item(UUID attemptId, String slug, String title, String coverUrl, List<String> topics, boolean available,
            Instant submittedAt, int score, int total, int elapsedSeconds) {
    }

    /**
     * @param attemptRevision the exercise revision the attempt was graded on
     * @param currentRevision the exercise revision now
     */
    record Stored(UUID attemptId, String slug, String title, boolean available, int attemptRevision,
            int currentRevision, Instant submittedAt, int score, int total, int elapsedSeconds,
            List<StoredAnswer> answers) {
    }

    /**
     * One graded answer as stored, plus what the question shows now.
     *
     * @param given        a {@code String} ("" if unanswered) or a {@code List<String>} of option keys
     * @param expected     the accepted values snapshotted when it was graded
     * @param family       {@code choiceFixed}, {@code gap}, {@code mcSingle}, {@code mcMulti} or {@code match}
     * @param chooseCount  how many numbers a "Choose N" question takes (null otherwise)
     * @param evidenceKey  the passage span that proves the answer, if any
     * @param explanations per language
     */
    record StoredAnswer(int number, Object given, List<String> expected, int points, int possible, String family,
            Integer chooseCount, String evidenceKey, Map<LanguageCode, String> explanations) {
    }
}
