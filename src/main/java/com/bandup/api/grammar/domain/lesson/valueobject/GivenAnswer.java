package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.List;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** What the learner answered: an option index (choose), a text (type, fix) or tiles in order (build). */
@ValueObject
public sealed interface GivenAnswer permits GivenAnswer.Choice, GivenAnswer.Text, GivenAnswer.Tiles {

    record Choice(int index) implements GivenAnswer {
    }

    record Text(String value) implements GivenAnswer {

        public Text {
            Objects.requireNonNull(value, "value");
        }
    }

    record Tiles(List<String> words) implements GivenAnswer {

        public Tiles {
            words = List.copyOf(words);
        }
    }
}
