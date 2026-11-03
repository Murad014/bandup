package com.bandup.api.vocabulary.domain.session.service;

import java.util.Locale;
import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.DomainService;

/**
 * Makes typed answers comparable: lower case, trimmed, inner spaces
 * collapsed, trailing {@code . ! ? , ;} dropped, curly apostrophes straight.
 */
@DomainService
public final class AnswerNormaliser {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.!?,;\\s]+$");

    public String normalise(String text) {
        if (text == null) {
            return "";
        }
        String straight = text.replace('’', '\'').replace('‘', '\'');
        String collapsed = SPACES.matcher(straight.strip()).replaceAll(" ");
        return TRAILING_PUNCTUATION.matcher(collapsed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
