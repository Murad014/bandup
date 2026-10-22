package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Objects;
import java.util.Optional;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.language.Translations;

/**
 * What the editor knows about one glossed word besides its surface text
 * (which is taken from the HTML): the dictionary form and its meanings.
 */
@ValueObject
public record GlossNote(String lemma, Translations<GlossMeaning> meanings) {

    public GlossNote {
        Objects.requireNonNull(meanings, "meanings");
        lemma = lemma == null || lemma.isBlank() ? null : lemma.strip();
    }

    public static GlossNote none() {
        return new GlossNote(null, Translations.empty());
    }

    public Optional<String> lemmaIfAny() {
        return Optional.ofNullable(lemma);
    }
}
