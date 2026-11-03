package com.bandup.api.vocabulary.domain.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.valueobject.Box;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.ReviewSchedule;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSource;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordState;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

class SavedWordTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);
    private final LearnerId leyla = new LearnerId(UUID.randomUUID());
    private final SavedWord word = SavedWord.save(leyla, new WordId(7), new WordSource("yellowstone-wolves", "  The wolves. "), DAY);

    @Test
    void aSavedWordIsNewUntilLearned() {
        assertThat(word.state()).isEqualTo(WordState.NEW);
        assertThat(word.box()).isEmpty();
        assertThat(word.isDue(DAY.plusDays(30))).isFalse();
        assertThat(word.source().sentence()).isEqualTo("The wolves.");
        assertThat(word.belongsTo(leyla)).isTrue();
        assertThat(word.belongsTo(new LearnerId(UUID.randomUUID()))).isFalse();
    }

    @Test
    void learningPutsItInBoxOneDueTomorrow() {
        word.learned(DAY);
        assertThat(word.state()).isEqualTo(WordState.LEARNING);
        assertThat(word.box()).contains(Box.first());
        assertThat(word.dueOn()).contains(DAY.plusDays(1));
        assertThat(word.isDue(DAY)).isFalse();
        assertThat(word.isDue(DAY.plusDays(1))).isTrue();
        word.learned(DAY.plusDays(5));
        assertThat(word.dueOn()).as("learning again changes nothing").contains(DAY.plusDays(1));
    }

    @Test
    void rightAnswersClimbTheBoxesUntilMastered() {
        word.learned(DAY);
        LocalDate day = DAY.plusDays(1);
        ReviewSchedule s = word.answer(true, day);
        assertThat(s.from().value()).isEqualTo(1);
        assertThat(s.box().value()).isEqualTo(2);
        assertThat(s.dueOn()).isEqualTo(day.plusDays(3));
        s = word.answer(true, day = day.plusDays(3));
        assertThat(s.dueOn()).isEqualTo(day.plusDays(7));
        s = word.answer(true, day = day.plusDays(7));
        assertThat(s.box().value()).isEqualTo(4);
        assertThat(s.dueOn()).isEqualTo(day.plusDays(14));
        s = word.answer(true, day.plusDays(14));
        assertThat(s.state()).isEqualTo(WordState.MASTERED);
        assertThat(s.dueOn()).isNull();
        assertThat(word.isDue(day.plusDays(100))).isFalse();
        assertThatThrownBy(() -> word.answer(true, DAY.plusDays(200))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aWrongAnswerGoesBackToBoxOne() {
        word.learned(DAY);
        word.answer(true, DAY.plusDays(1));
        ReviewSchedule s = word.answer(false, DAY.plusDays(4));
        assertThat(s.from().value()).isEqualTo(2);
        assertThat(s.box().value()).isEqualTo(1);
        assertThat(s.dueOn()).isEqualTo(DAY.plusDays(5));
    }

    @Test
    void onlyTheFirstAnswerOfADayCounts() {
        word.learned(DAY);
        ReviewSchedule wrong = word.answer(false, DAY.plusDays(1));
        assertThat(wrong.changed()).isTrue();
        ReviewSchedule again = word.answer(true, DAY.plusDays(1));
        assertThat(again.changed()).isFalse();
        assertThat(again.box().value()).isEqualTo(1);
        SavedWord fresh = SavedWord.save(leyla, new WordId(8), WordSource.typed(), DAY);
        fresh.learned(DAY);
        assertThat(fresh.answer(true, DAY).changed()).as("learned today counts as today's answer").isFalse();
    }

    @Test
    void itCanPointToAnotherCard() {
        word.pointTo(new WordId(9));
        assertThat(word.word()).isEqualTo(new WordId(9));
    }
}
