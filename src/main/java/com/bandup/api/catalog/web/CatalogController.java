package com.bandup.api.catalog.web;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.catalog.application.BrowseExercises;
import com.bandup.api.catalog.application.CatalogFilters;
import com.bandup.api.catalog.application.CatalogPage;
import com.bandup.api.catalog.application.CatalogQuery;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.web.RequestLanguage;

/**
 * {@code GET /api/v1/exercises} (database design §5.0) and the filter choices
 * for the catalog page. Public; a valid token adds done / best score / notDone.
 */
@RestController
@RequestMapping("/api/v1/exercises")
@RequiredArgsConstructor
class CatalogController {

    private final BrowseExercises browse;
    private final CurrentUser currentUser;

    @GetMapping
    CatalogPage list(@RequestParam(required = false) String skill, @RequestParam(required = false) String format,
            @RequestParam(name = "level", required = false) List<String> levels,
            @RequestParam(name = "type", required = false) List<String> types,
            @RequestParam(name = "topic", required = false) List<String> topics,
            @RequestParam(required = false) Integer maxMinutes, @RequestParam(defaultValue = "false") boolean notDone,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return browse.browse(new CatalogQuery(skill, format, levels, types, topics, maxMinutes, notDone, page, size,
                language(lang, acceptLanguage)));
    }

    @GetMapping("/filters")
    CatalogFilters filters(@RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return browse.filters(language(lang, acceptLanguage));
    }

    private String language(String lang, String acceptLanguage) {
        Optional<String> userLanguage = currentUser.get().flatMap(c -> c.supportLanguage()).map(code -> code.value());
        return RequestLanguage.pick(lang, userLanguage, acceptLanguage);
    }
}
