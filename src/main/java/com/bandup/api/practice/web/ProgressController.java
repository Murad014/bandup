package com.bandup.api.practice.web;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.practice.application.LearnerHistory;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.web.RequestLanguage;

/**
 * "My progress" (requirement 11, design §7.5), signed-in learners only:
 * {@code GET /api/v1/me/progress}, {@code GET /api/v1/me/attempts},
 * {@code GET /api/v1/me/attempts/{id}}.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
class ProgressController {

    private final LearnerHistory history;
    private final CurrentUser currentUser;

    @GetMapping("/progress")
    LearnerHistory.Summary progress() {
        return history.summary();
    }

    /** @param passageId only this passage's attempts */
    @GetMapping("/attempts")
    LearnerHistory.Page attempts(@RequestParam(required = false) String passageId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return history.attempts(passageId, page, size);
    }

    /** The result screen again; {@code result} has the same shape as the submit response. */
    record ReviewResponse(String passageId, String title, boolean available, boolean updatedSince,
            java.time.Instant submittedAt, AttemptResponse result) {
    }

    @GetMapping("/attempts/{id}")
    ReviewResponse review(@PathVariable String id, @RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        UUID attemptId;
        try {
            attemptId = UUID.fromString(id);
        } catch (IllegalArgumentException notAnId) {
            throw new NotFoundException("Attempt not found.");
        }
        Optional<String> userLanguage = currentUser.get().flatMap(c -> c.supportLanguage()).map(code -> code.value());
        LearnerHistory.Review review = history.review(attemptId, RequestLanguage.pick(lang, userLanguage, acceptLanguage));
        return new ReviewResponse(review.passageId(), review.title(), review.available(), review.updatedSince(),
                review.submittedAt(), AttemptResponse.of(review.result()));
    }
}
