package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * Everything one save of a lesson carries (the lesson format,
 * {@code lesson-format.md}), before the {@code LessonPolicy} has checked it.
 *
 * @param position may be null: set on publish, at the end of the course
 */
@ValueObject
public record LessonBody(GrammarLevel level, Topic topic, LessonText title, LessonText goal, int minutes,
        Position position, List<LearnCard> learn, WorkedExample worked, List<Item> practice, List<Item> check) {

    public static final int DEFAULT_MINUTES = 20;

    public LessonBody {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(goal, "goal");
        learn = List.copyOf(learn);
        Objects.requireNonNull(worked, "worked");
        practice = List.copyOf(practice);
        check = List.copyOf(check);
    }

    public Optional<Position> wantedPosition() {
        return Optional.ofNullable(position);
    }
}
