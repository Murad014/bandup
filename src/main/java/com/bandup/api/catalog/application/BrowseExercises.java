package com.bandup.api.catalog.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.catalog.application.port.ExerciseCatalog;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.domain.error.Violation;

/**
 * Browse and filter published exercises (database requirement 7.4). Anyone
 * may browse; a signed-in learner also sees what they've done and can hide it.
 * Malformed filters are a 400 listing every problem.
 */
@Service
public class BrowseExercises {

    public static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> SKILLS = Set.of("READING", "LISTENING", "WRITING", "SPEAKING", "GRAMMAR");
    private static final Set<String> FORMATS = Set.of("MINI", "FULL");
    private static final Set<String> LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z_]{0,31}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final ExerciseCatalog catalog;
    private final CurrentUser currentUser;
    private final LanguageCatalog languages;

    public BrowseExercises(ExerciseCatalog catalog, CurrentUser currentUser, LanguageCatalog languages) {
        this.catalog = catalog;
        this.currentUser = currentUser;
        this.languages = languages;
    }

    @Transactional(readOnly = true)
    public CatalogPage browse(CatalogQuery query) {
        check(query);
        Optional<UUID> learner = currentUser.get().map(c -> c.userId());
        return catalog.find(query, learner, languages.fallbacks().chainFor(query.requestedLanguage()));
    }

    @Transactional(readOnly = true)
    public CatalogFilters filters(String requestedLanguage) {
        return catalog.filters(languages.fallbacks().chainFor(requestedLanguage));
    }

    private static void check(CatalogQuery q) {
        List<Violation> problems = new ArrayList<>();
        if (q.skill() != null && !SKILLS.contains(q.skill())) {
            problems.add(new Violation("skill", "Unknown skill '" + q.skill() + "'."));
        }
        if (q.format() != null && !FORMATS.contains(q.format())) {
            problems.add(new Violation("format", "Use MINI or FULL."));
        }
        q.levels().stream().filter(l -> !LEVELS.contains(l))
                .forEach(l -> problems.add(new Violation("level", "Unknown level '" + l + "'.")));
        q.types().stream().filter(t -> !CODE.matcher(t).matches())
                .forEach(t -> problems.add(new Violation("type", "Unknown question type '" + t + "'.")));
        q.topics().stream().filter(t -> !SLUG.matcher(t).matches())
                .forEach(t -> problems.add(new Violation("topic", "Unknown topic '" + t + "'.")));
        if (q.maxMinutes() != null && q.maxMinutes() < 1) {
            problems.add(new Violation("maxMinutes", "Use 1 minute or more."));
        }
        if (q.page() < 0) {
            problems.add(new Violation("page", "Pages start at 0."));
        }
        if (q.size() < 1 || q.size() > MAX_PAGE_SIZE) {
            problems.add(new Violation("size", "Use 1 to " + MAX_PAGE_SIZE + " per page."));
        }
        if (!problems.isEmpty()) {
            throw new InvalidRequestException("The filters are invalid.", problems);
        }
    }
}
