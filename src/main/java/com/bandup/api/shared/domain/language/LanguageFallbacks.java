package com.bandup.api.shared.domain.language;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.bandup.api.shared.domain.ddd.DomainService;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * Builds {@link LanguageChain}s from the known languages and their fallbacks
 * (the {@code language.fallback_code} data).
 *
 * <ul>
 *   <li>Codes match case-insensitively; the chain uses the stored spelling.</li>
 *   <li>An unknown regional code uses its base language ({@code az-AZ} ->
 *       {@code az}); anything else unknown, malformed, blank or null gets
 *       English only. Requested codes come from users, so bad input never
 *       throws.</li>
 *   <li>The walk stops at English, and English is appended if the data never
 *       reaches it, so every chain ends with English exactly once. A loop in
 *       the data stops instead of running forever.</li>
 * </ul>
 */
@DomainService
public final class LanguageFallbacks {

    /** Any-case code -> the code as stored. */
    private final Map<LanguageCode, LanguageCode> stored = new HashMap<>();

    /** Stored code -> its fallback. */
    private final Map<LanguageCode, LanguageCode> fallbackOf = new HashMap<>();

    private LanguageFallbacks(Map<LanguageCode, Optional<LanguageCode>> languages) {
        languages.forEach((code, fallback) -> {
            stored.put(code, code);
            fallback.ifPresent(f -> fallbackOf.put(code, f));
        });
    }

    /** @param languages every known language mapped to its fallback (empty = end of chain) */
    public static LanguageFallbacks of(Map<LanguageCode, Optional<LanguageCode>> languages) {
        return new LanguageFallbacks(languages);
    }

    /** The chain for a requested code as typed by a user or sent by a browser. */
    public LanguageChain chainFor(String requested) {
        if (requested == null || requested.isBlank()) {
            return LanguageChain.englishOnly();
        }
        try {
            return chainFor(LanguageCode.of(requested));
        } catch (DomainException malformed) {
            return LanguageChain.englishOnly();
        }
    }

    public LanguageChain chainFor(LanguageCode requested) {
        List<LanguageCode> chain = new ArrayList<>();
        Set<LanguageCode> seen = new HashSet<>();
        LanguageCode code = known(requested);
        while (code != null && !code.equals(LanguageCode.ENGLISH) && seen.add(code)) {
            chain.add(code);
            LanguageCode fallback = fallbackOf.get(code);
            code = fallback == null ? null : stored.getOrDefault(fallback, fallback);
        }
        chain.add(stored.getOrDefault(LanguageCode.ENGLISH, LanguageCode.ENGLISH));
        return LanguageChain.of(chain);
    }

    /** True if this exact language (any case) is one of the known languages. */
    public boolean isKnown(LanguageCode code) {
        return stored.containsKey(code);
    }

    /** The stored spelling of a known language (e.g. {@code pt-BR} for {@code PT-br}). */
    public Optional<LanguageCode> storedSpelling(LanguageCode code) {
        return Optional.ofNullable(stored.get(code));
    }

    /** The stored code for a requested one, trying the base language of a regional code; null if unknown. */
    private LanguageCode known(LanguageCode requested) {
        LanguageCode code = stored.get(requested);
        if (code == null && requested.isRegional()) {
            code = stored.get(requested.base());
        }
        return code;
    }
}
