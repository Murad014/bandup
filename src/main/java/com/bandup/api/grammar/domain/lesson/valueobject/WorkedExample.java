package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.List;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The think-aloud example before practice. A teaching step, so learners see its answer. */
@ValueObject
public record WorkedExample(LessonText question, List<LessonText> steps, String answer) {

    public WorkedExample {
        Objects.requireNonNull(question, "question");
        steps = List.copyOf(steps);
        Objects.requireNonNull(answer, "answer");
    }
}
