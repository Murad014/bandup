package com.bandup.api.practice.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.practice.application.port.LearnerPassages;
import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.application.NotFoundException;

/**
 * The learner's view of a published reading exercise, with help text in the
 * requested language and item-by-item fallback (backend requirements 2, 6).
 */
@Service
public class GetLearnerPassage {

    private final LearnerPassages passages;
    private final LanguageCatalog languages;

    public GetLearnerPassage(LearnerPassages passages, LanguageCatalog languages) {
        this.passages = passages;
        this.languages = languages;
    }

    /**
     * @param requestedLanguage the help language the request asks for (may be
     *                          null, unknown or malformed: falls back to English)
     * @throws NotFoundException if the exercise doesn't exist, isn't PUBLISHED or isn't READING
     */
    @Transactional(readOnly = true)
    public PassageView get(String slug, String requestedLanguage) {
        return passages.find(slug, languages.fallbacks().chainFor(requestedLanguage))
                .orElseThrow(() -> new NotFoundException("Passage not found."));
    }
}
