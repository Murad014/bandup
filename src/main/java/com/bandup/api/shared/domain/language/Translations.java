package com.bandup.api.shared.domain.language;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * Help text in several languages: at most one value per language. Immutable;
 * {@link #with} returns a new instance.
 *
 * @param <T> the translated value, e.g. a gloss meaning or an explanation
 */
@ValueObject
public final class Translations<T> {

    private static final Translations<?> EMPTY = new Translations<>(Map.of());

    private final Map<LanguageCode, T> values;

    private Translations(Map<LanguageCode, T> values) {
        this.values = values;
    }

    @SuppressWarnings("unchecked")
    public static <T> Translations<T> empty() {
        return (Translations<T>) EMPTY;
    }

    public static <T> Translations<T> of(Map<LanguageCode, T> values) {
        Map<LanguageCode, T> copy = new LinkedHashMap<>();
        values.forEach((code, value) -> copy.put(Objects.requireNonNull(code, "language"), Objects.requireNonNull(value, "value")));
        return new Translations<>(Collections.unmodifiableMap(copy));
    }

    /** A copy with {@code value} for {@code language}, replacing any existing one. */
    public Translations<T> with(LanguageCode language, T value) {
        Map<LanguageCode, T> copy = new LinkedHashMap<>(values);
        copy.remove(language); // keep the new spelling if only the case differs
        copy.put(Objects.requireNonNull(language, "language"), Objects.requireNonNull(value, "value"));
        return new Translations<>(Collections.unmodifiableMap(copy));
    }

    public Optional<T> get(LanguageCode language) {
        return Optional.ofNullable(values.get(language));
    }

    public Set<LanguageCode> languages() {
        return values.keySet();
    }

    public Map<LanguageCode, T> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Translations<?> t && values.equals(t.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
