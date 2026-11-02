package com.bandup.api.grammar.domain.review.aggregate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.review.valueobject.Box;
import com.bandup.api.grammar.domain.review.valueobject.ReviewCardId;
import com.bandup.api.grammar.domain.review.valueobject.ReviewSchedule;
import com.bandup.api.shared.domain.ddd.AggregateRoot;

/**
 * One check item of a finished lesson in a learner's spaced review
 * (aggregate root; grammar requirement 6). Each card is its own aggregate:
 * a deck can hold thousands, and answering one must not load the rest.
 * Only the first answer of a day changes the schedule; the session repeats a
 * wrong card until it is right without moving it again.
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ReviewCard {

    @EqualsAndHashCode.Include
    @Getter
    private final ReviewCardId id;
    @Getter
    private final LearnerId learner;
    @Getter
    private final LessonId lesson;
    @Getter
    private final ItemId item;
    @Getter
    private Box box;
    @Getter
    private LocalDate dueOn;
    private LocalDate lastAnsweredOn;

    private ReviewCard(ReviewCardId id, LearnerId learner, LessonId lesson, ItemId item, Box box, LocalDate dueOn,
            LocalDate lastAnsweredOn) {
        this.id = Objects.requireNonNull(id, "id");
        this.learner = Objects.requireNonNull(learner, "learner");
        this.lesson = Objects.requireNonNull(lesson, "lesson");
        this.item = Objects.requireNonNull(item, "item");
        this.box = Objects.requireNonNull(box, "box");
        this.dueOn = Objects.requireNonNull(dueOn, "dueOn");
        this.lastAnsweredOn = lastAnsweredOn;
    }

    /** A new card in box 1, due tomorrow (requirement 6.1). */
    public static ReviewCard create(LearnerId learner, LessonId lesson, ItemId item, LocalDate today) {
        Box first = Box.first();
        return new ReviewCard(ReviewCardId.random(), learner, lesson, item, first, first.dueAfter(today), null);
    }

    public static ReviewCard restore(ReviewCardId id, LearnerId learner, LessonId lesson, ItemId item, Box box,
            LocalDate dueOn, LocalDate lastAnsweredOn) {
        return new ReviewCard(id, learner, lesson, item, box, dueOn, lastAnsweredOn);
    }

    /** Moves the card after an answer (requirements 6.4, 6.5). */
    public ReviewSchedule answer(boolean correct, LocalDate today) {
        if (today.equals(lastAnsweredOn)) {
            return new ReviewSchedule(box, dueOn, false);
        }
        box = correct ? box.up() : Box.first();
        dueOn = box.dueAfter(today);
        lastAnsweredOn = today;
        return new ReviewSchedule(box, dueOn, true);
    }

    public boolean isDue(LocalDate today) {
        return !dueOn.isAfter(today);
    }

    public boolean belongsTo(LearnerId caller) {
        return learner.equals(caller);
    }

    public Optional<LocalDate> lastAnsweredOn() {
        return Optional.ofNullable(lastAnsweredOn);
    }
}
