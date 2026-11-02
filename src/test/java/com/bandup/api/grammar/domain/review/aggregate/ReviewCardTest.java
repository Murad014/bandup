package com.bandup.api.grammar.domain.review.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.review.valueobject.Box;
import com.bandup.api.grammar.domain.review.valueobject.ReviewSchedule;

class ReviewCardTest {

    private static final LearnerId ANNA = new LearnerId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    private ReviewCard card() {
        return ReviewCard.create(ANNA, new LessonId(1), new ItemId("c3"), DAY);
    }

    @Test
    void aNewCardIsInBoxOneAndDueTomorrow() {
        ReviewCard card = card();
        assertThat(card.box()).isEqualTo(Box.first());
        assertThat(card.dueOn()).isEqualTo(DAY.plusDays(1));
        assertThat(card.isDue(DAY)).isFalse();
        assertThat(card.isDue(DAY.plusDays(1))).isTrue();
        assertThat(card.isDue(DAY.plusDays(5))).as("overdue is still due").isTrue();
    }

    @Test
    void correctAnswersClimbTheBoxesWithLongerGapsAndBoxFourStays() {
        ReviewCard card = card();
        LocalDate day = DAY.plusDays(1);
        assertThat(card.answer(true, day)).isEqualTo(new ReviewSchedule(new Box(2), day.plusDays(3), true));
        day = day.plusDays(3);
        assertThat(card.answer(true, day)).isEqualTo(new ReviewSchedule(new Box(3), day.plusDays(7), true));
        day = day.plusDays(7);
        assertThat(card.answer(true, day)).isEqualTo(new ReviewSchedule(new Box(4), day.plusDays(14), true));
        day = day.plusDays(14);
        assertThat(card.answer(true, day)).isEqualTo(new ReviewSchedule(new Box(4), day.plusDays(14), true));
    }

    @Test
    void aWrongAnswerSendsTheCardBackToBoxOne() {
        ReviewCard card = card();
        card.answer(true, DAY.plusDays(1));
        assertThat(card.answer(false, DAY.plusDays(4))).isEqualTo(new ReviewSchedule(Box.first(), DAY.plusDays(5), true));
    }

    @Test
    void onlyTheFirstAnswerOfADayMovesTheCard() {
        ReviewCard card = card();
        LocalDate day = DAY.plusDays(1);
        card.answer(false, day);
        assertThat(card.answer(true, day)).isEqualTo(new ReviewSchedule(Box.first(), day.plusDays(1), false));
        assertThat(card.lastAnsweredOn()).contains(day);
        assertThat(card.answer(true, day.plusDays(1)).changed()).isTrue();
    }

    @Test
    void aCardBelongsToItsLearner() {
        assertThat(card().belongsTo(ANNA)).isTrue();
        assertThat(card().belongsTo(new LearnerId(UUID.randomUUID()))).isFalse();
    }
}
