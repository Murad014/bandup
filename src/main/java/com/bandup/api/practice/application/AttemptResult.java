package com.bandup.api.practice.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A graded attempt as the learner sees it after submitting (backend
 * requirement 3.4): only now are answers, explanations and evidence shown.
 *
 * @param percent whole percentage, rounded half up
 */
public record AttemptResult(UUID attemptId, int score, int total, int percent, int elapsedSeconds,
        List<QuestionResult> results) {

    public AttemptResult {
        results = List.copyOf(results);
    }

    /**
     * @param n               the question's first number
     * @param nTo             its last number when it takes several ("Choose TWO": 7–8)
     * @param given           what the learner sent: a {@code String} (empty if unanswered) or a
     *                        {@code List<String>} of option keys
     * @param answer          the correct answer to show: the first accepted value, or the keys
     *                        joined with ", " for "Choose N"
     * @param acceptedAnswers every accepted value / required key
     * @param explanation     in the learner's help language with fallback, if any
     * @param evidenceId      the passage span ({@code ev} id) that proves the answer, if any
     */
    public record QuestionResult(int n, Optional<Integer> nTo, boolean correct, int points, int possible,
            Object given, String answer, List<String> acceptedAnswers, Optional<String> explanation,
            Optional<String> evidenceId) {

        public QuestionResult {
            acceptedAnswers = List.copyOf(acceptedAnswers);
        }
    }
}
