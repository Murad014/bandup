package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * One teaching card of the Learn stage: a heading with a body, decision steps
 * and/or ✓/✗ examples, and an optional tip.
 *
 * @param body  may be null
 * @param tip   may be null
 */
@ValueObject
public record LearnCard(String icon, LessonText label, LessonText heading, LessonText body,
        List<LessonText> steps, List<Example> examples, LessonText tip) {

    public LearnCard {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(heading, "heading");
        steps = List.copyOf(steps);
        examples = List.copyOf(examples);
    }

    public Optional<LessonText> bodyText() {
        return Optional.ofNullable(body);
    }

    public Optional<LessonText> tipText() {
        return Optional.ofNullable(tip);
    }

    /** A correct (✓) or wrong (✗) example sentence. */
    public record Example(boolean ok, LessonText text) {

        public Example {
            Objects.requireNonNull(text, "text");
        }
    }
}
