package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.Objects;
import java.util.Optional;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.language.Translations;

/**
 * One question as Practice needs it for grading and for the result: its id,
 * how it's scored, the passage evidence it points to and its explanations.
 *
 * @param evidenceKey  the passage's {@code ev} id (e.g. "e4"), or null
 * @param explanations explanation HTML per language (resolved by the use case)
 */
@ValueObject
public record GradableQuestion(QuestionId id, GradingRule rule, String evidenceKey, Translations<String> explanations) {

    public GradableQuestion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(explanations, "explanations");
    }

    public Optional<String> evidence() {
        return Optional.ofNullable(evidenceKey);
    }
}
