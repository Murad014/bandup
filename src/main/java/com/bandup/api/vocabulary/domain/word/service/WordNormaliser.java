package com.bandup.api.vocabulary.domain.word.service;

import java.util.Locale;
import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.DomainService;

/**
 * Makes typed or selected text comparable (vocabulary requirement 3.1): lower
 * case, trimmed, inner spaces collapsed, curly apostrophes straight, and
 * surrounding punctuation dropped ("Riverbanks, " → "riverbanks").
 */
@DomainService
public final class WordNormaliser {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\p{Punct}“”‘’«»\\s]+|[\\p{Punct}“”‘’«»\\s]+$");

    public String normalise(String text) {
        if (text == null) {
            return "";
        }
        String straight = text.replace('’', '\'').replace('‘', '\'');
        String trimmed = EDGE_PUNCTUATION.matcher(straight.strip()).replaceAll("");
        return SPACES.matcher(trimmed).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
