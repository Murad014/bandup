package com.bandup.api.practice.web;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.bandup.api.practice.application.AttemptResult;

/** The graded attempt JSON ({@code reading-practice-ui} design §4.2, plus points and number ranges). */
record AttemptResponse(UUID attemptId, int score, int total, int percent, int elapsedSeconds, List<Result> results) {

    static AttemptResponse of(AttemptResult result) {
        return new AttemptResponse(result.attemptId(), result.score(), result.total(), result.percent(),
                result.elapsedSeconds(), result.results().stream().map(Result::of).toList());
    }

    /** {@code given} is always sent ("" when unanswered); optional fields are left out when empty. */
    record Result(int n, @JsonInclude(JsonInclude.Include.NON_NULL) Integer nTo, boolean correct, int points,
            int possible, Object given, String answer, List<String> acceptedAnswers,
            @JsonInclude(JsonInclude.Include.NON_NULL) String explanation,
            @JsonInclude(JsonInclude.Include.NON_NULL) String evidenceId) {

        static Result of(AttemptResult.QuestionResult r) {
            return new Result(r.n(), r.nTo().orElse(null), r.correct(), r.points(), r.possible(), r.given(),
                    r.answer(), r.acceptedAnswers(), r.explanation().orElse(null), r.evidenceId().orElse(null));
        }
    }
}
