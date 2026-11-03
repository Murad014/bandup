package com.bandup.api.vocabulary.domain.session.valueobject;

import java.util.List;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** What a learner answered: an option, a typed text, or the meaning picked for each word of a match. */
@ValueObject
public sealed interface GivenAnswer {

    @ValueObject
    record Choice(int index) implements GivenAnswer {
    }

    @ValueObject
    record Text(String value) implements GivenAnswer {

        public Text {
            Objects.requireNonNull(value, "value");
        }
    }

    /** {@code picks.get(i)}: the index of the meaning picked for the i-th word. */
    @ValueObject
    record Pairs(List<Integer> picks) implements GivenAnswer {

        public Pairs {
            picks = List.copyOf(picks);
        }
    }
}
