package com.bandup.api.vocabulary.domain.word.valueobject;

import java.util.List;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * Everything a card teaches and tests (card-format.md), checked by
 * {@code CardPolicy}. Plain text only.
 */
@ValueObject
public record CardContent(String pos, String level, String ipa, Meaning meaning, List<String> examples,
        List<String> collocations, List<String> family, GapTask gap, PairTask pair, List<String> wrongMeanings) {

    public CardContent {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(ipa, "ipa");
        Objects.requireNonNull(meaning, "meaning");
        Objects.requireNonNull(gap, "gap");
        Objects.requireNonNull(pair, "pair");
        examples = List.copyOf(examples);
        collocations = List.copyOf(collocations);
        family = List.copyOf(family);
        wrongMeanings = List.copyOf(wrongMeanings);
    }
}
