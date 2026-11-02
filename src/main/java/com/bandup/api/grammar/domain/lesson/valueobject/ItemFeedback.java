package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.Objects;
import java.util.Optional;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * The server's verdict on one answer (grammar requirement 5.5), in the
 * learner's language.
 *
 * @param expected    the correct answer to show
 * @param why         for a wrong {@code choose} option: why that option is wrong; may be null
 * @param explanation the item's explanation
 */
@ValueObject
public record ItemFeedback(boolean correct, String expected, String why, String explanation) {

    public ItemFeedback {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(explanation, "explanation");
    }

    public Optional<String> whyText() {
        return Optional.ofNullable(why);
    }
}
