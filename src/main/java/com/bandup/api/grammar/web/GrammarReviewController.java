package com.bandup.api.grammar.web;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.grammar.application.GrammarReview;
import com.bandup.api.grammar.application.view.LearnerViews.ReviewAnswerView;
import com.bandup.api.grammar.application.view.LearnerViews.ReviewView;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.web.RequestLanguage;

/** The spaced review API (grammar design §5.1); a token is required. */
@RestController
@RequestMapping("/api/v1/grammar/review")
@RequiredArgsConstructor
class GrammarReviewController {

    private final GrammarReview review;
    private final CurrentUser currentUser;

    @GetMapping
    ReviewView due(@RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return review.due(language(lang, acceptLanguage));
    }

    @PostMapping("/{cardId}/answer")
    ReviewAnswerView answer(@PathVariable UUID cardId, @RequestBody AnswerRequest body,
            @RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return review.answer(cardId, body.answer(), language(lang, acceptLanguage));
    }

    private String language(String lang, String acceptLanguage) {
        Optional<String> userLanguage = currentUser.get().flatMap(c -> c.supportLanguage()).map(code -> code.value());
        return RequestLanguage.pick(lang, userLanguage, acceptLanguage);
    }

    record AnswerRequest(Object answer) {
    }
}
