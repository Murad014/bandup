package com.bandup.api.practice.web;

import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.practice.application.GetLearnerPassage;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.web.RequestLanguage;

/** {@code GET /api/v1/passages/{slug}}: a passage without answers (public). */
@RestController
@RequestMapping("/api/v1/passages")
@RequiredArgsConstructor
class PassageController {

    private final GetLearnerPassage getLearnerPassage;
    private final CurrentUser currentUser;

    @GetMapping("/{slug}")
    PassageResponse get(@PathVariable String slug, @RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return PassageResponse.of(getLearnerPassage.get(slug, requestedLanguage(lang, acceptLanguage)));
    }

    private String requestedLanguage(String lang, String acceptLanguage) {
        Optional<String> userLanguage = currentUser.get().flatMap(c -> c.supportLanguage()).map(code -> code.value());
        return RequestLanguage.pick(lang, userLanguage, acceptLanguage);
    }
}
