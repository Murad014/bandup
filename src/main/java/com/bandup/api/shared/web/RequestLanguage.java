package com.bandup.api.shared.web;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.bandup.api.shared.domain.language.LanguageFallbacks;

/**
 * Picks the help language a request asks for (requirement 6.1):
 * an explicit {@code ?lang=} / body value, else the signed-in user's support
 * language, else the browser's first {@code Accept-Language} tag, else none
 * (which the language chain turns into English).
 *
 * <p>The result is the raw requested code; it may be unknown or malformed, and
 * {@link com.bandup.api.shared.domain.language.LanguageFallbacks} handles that safely.
 */
public final class RequestLanguage {

    private RequestLanguage() {
    }

    /**
     * @param explicit       {@code ?lang=} or the request body's language; may be null
     * @param userLanguage   the signed-in user's support language; empty when anonymous
     * @param acceptLanguage the {@code Accept-Language} header; may be null
     * @return the requested code, or null to use English
     */
    public static String pick(String explicit, Optional<String> userLanguage, String acceptLanguage) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        if (userLanguage.isPresent() && !userLanguage.get().isBlank()) {
            return userLanguage.get();
        }
        return firstAcceptedLanguage(acceptLanguage);
    }

    /** The highest-weighted concrete tag of an Accept-Language header ("*" is skipped). */
    private static String firstAcceptedLanguage(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
            return ranges.stream()
                    .map(Locale.LanguageRange::getRange)
                    .filter(range -> !range.startsWith("*"))
                    .findFirst()
                    .orElse(null);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
