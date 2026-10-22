package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.List;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * What the learner submitted for one question, exactly as given: a text
 * ("FALSE", "riverbanks", "B") or, for "Choose N" questions, option keys.
 * Stored unchanged in the attempt; only the {@code Grader} normalizes.
 */
@ValueObject
public sealed interface GivenAnswer permits GivenAnswer.Text, GivenAnswer.Keys {

    /** Nothing was answered. */
    static GivenAnswer blank() {
        return new Text("");
    }

    /** The answer as one text; several keys can't be one answer, so they read as blank. */
    String asText();

    /** The answer as option keys; a non-blank text is one key. */
    List<String> asKeys();

    default boolean isBlank() {
        return asKeys().isEmpty();
    }

    record Text(String value) implements GivenAnswer {

        public Text {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String asText() {
            return value;
        }

        @Override
        public List<String> asKeys() {
            return value.isBlank() ? List.of() : List.of(value);
        }
    }

    record Keys(List<String> keys) implements GivenAnswer {

        public Keys {
            keys = keys.stream().map(key -> Objects.requireNonNull(key, "key")).filter(key -> !key.isBlank()).toList();
        }

        @Override
        public String asText() {
            return keys.size() == 1 ? keys.getFirst() : "";
        }

        @Override
        public List<String> asKeys() {
            return keys;
        }
    }
}
