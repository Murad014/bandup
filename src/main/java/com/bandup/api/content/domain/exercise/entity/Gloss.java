package com.bandup.api.content.domain.exercise.entity;

import java.util.Objects;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.content.domain.exercise.valueobject.GlossKey;
import com.bandup.api.content.domain.exercise.valueobject.GlossMeaning;
import com.bandup.api.shared.domain.ddd.Entity;
import com.bandup.api.shared.domain.language.Translations;

/**
 * A glossed word of the passage, identified by its key. Immutable; the
 * {@code Exercise} rebuilds the passage's glosses whenever the HTML changes.
 *
 * @param surface the word as it appears in the passage ("eliminated")
 * @param lemma   its dictionary form ("eliminate"), or null
 */
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Gloss {

    @EqualsAndHashCode.Include
    @Getter
    private final GlossKey key;
    @Getter
    private final String surface;
    private final String lemma;
    /** Meaning per help language. */
    @Getter
    private final Translations<GlossMeaning> meanings;

    public Gloss(GlossKey key, String surface, String lemma, Translations<GlossMeaning> meanings) {
        this.key = Objects.requireNonNull(key, "key");
        this.surface = Objects.requireNonNull(surface, "surface");
        this.lemma = lemma;
        this.meanings = Objects.requireNonNull(meanings, "meanings");
    }

    public Optional<String> lemma() {
        return Optional.ofNullable(lemma);
    }

    @Override
    public String toString() {
        return "Gloss[" + key + " " + surface + "]";
    }
}
