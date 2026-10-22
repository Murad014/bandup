package com.bandup.api.shared.domain.language;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * The languages to try, in order, for one request: {@code [tr, en]},
 * {@code [pt-BR, pt, en]}. Always ends with English, and lists each language
 * once. Help text is resolved item by item: each gloss, explanation or title
 * takes the first language in the chain that has it.
 */
@ValueObject
public final class LanguageChain {

    private final List<LanguageCode> codes;

    private LanguageChain(List<LanguageCode> codes) {
        this.codes = codes;
    }

    /** @throws IllegalArgumentException unless the list is non-empty, has no repeats and ends with English */
    public static LanguageChain of(List<LanguageCode> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new IllegalArgumentException("A language chain needs at least one language");
        }
        if (new HashSet<>(codes).size() != codes.size()) {
            throw new IllegalArgumentException("A language appears twice in " + codes);
        }
        if (!codes.get(codes.size() - 1).equals(LanguageCode.ENGLISH)) {
            throw new IllegalArgumentException("A language chain must end with English: " + codes);
        }
        return new LanguageChain(List.copyOf(codes));
    }

    public static LanguageChain englishOnly() {
        return new LanguageChain(List.of(LanguageCode.ENGLISH));
    }

    public List<LanguageCode> codes() {
        return codes;
    }

    /** The language actually used for this request (first in the chain). */
    public LanguageCode primary() {
        return codes.get(0);
    }

    /** The value in the first chain language that has one. */
    public <T> Optional<T> pick(Translations<T> translations) {
        for (LanguageCode code : codes) {
            Optional<T> value = translations.get(code);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves many items at once (e.g. all glosses of a passage), keeping their
     * order. Items with nothing in any chain language are left out.
     */
    public <K, T> Map<K, T> pickAll(Map<K, Translations<T>> items) {
        Map<K, T> resolved = new LinkedHashMap<>();
        items.forEach((key, translations) -> pick(translations).ifPresent(value -> resolved.put(key, value)));
        return resolved;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LanguageChain chain && codes.equals(chain.codes);
    }

    @Override
    public int hashCode() {
        return codes.hashCode();
    }

    @Override
    public String toString() {
        return codes.toString();
    }
}
