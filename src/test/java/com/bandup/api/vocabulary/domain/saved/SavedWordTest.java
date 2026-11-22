package com.bandup.api.vocabulary.domain.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.SetStep;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSource;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordState;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/** A saved word: NEW → LEARNING (in the sets) → MASTERED after 2 rounds in a row (requirement 6.6). */
class SavedWordTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);
    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private final LearnerId leyla = new LearnerId(UUID.randomUUID());
    private final SavedWord word = SavedWord.save(leyla, new WordId(7), new WordSource("yellowstone-wolves", "  The wolves. "), DAY);

    @Test
    void aSavedWordIsNewUntilLearned() {
        assertThat(word.state()).isEqualTo(WordState.NEW);
        assertThat(word.learnedAt()).isEmpty();
        assertThat(word.streak()).isZero();
        assertThat(word.source().sentence()).isEqualTo("The wolves.");
        assertThat(word.belongsTo(leyla)).isTrue();
        assertThat(word.belongsTo(new LearnerId(UUID.randomUUID()))).isFalse();
        assertThatThrownBy(() -> word.roundAnswer(true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void learningPutsItInTheSets() {
        word.learned(NOW);
        assertThat(word.state()).isEqualTo(WordState.LEARNING);
        assertThat(word.learnedAt()).contains(NOW);
        word.learned(NOW.plusSeconds(60));
        assertThat(word.learnedAt()).as("learning again changes nothing").contains(NOW);
    }

    @Test
    void twoRoundsInARowRightOnTheFirstTryMasterIt() {
        word.learned(NOW);
        SetStep once = word.roundAnswer(true);
        assertThat(once).isEqualTo(new SetStep(1, WordState.LEARNING, true));
        SetStep twice = word.roundAnswer(true);
        assertThat(twice).isEqualTo(new SetStep(2, WordState.MASTERED, true));
        assertThat(twice.mastered()).isTrue();
        assertThat(word.rounds()).isEqualTo(2);
    }

    @Test
    void aMissStartsItAgainFromZero() {
        word.learned(NOW);
        word.roundAnswer(true);
        SetStep miss = word.roundAnswer(false);
        assertThat(miss).isEqualTo(new SetStep(0, WordState.LEARNING, true));
        assertThat(word.roundAnswer(true).streak()).as("right, wrong, right isn't 2 in a row").isEqualTo(1);
        assertThat(word.state()).isEqualTo(WordState.LEARNING);
        assertThat(word.rounds()).isEqualTo(3);
    }

    @Test
    void aMasteredWordDoesNotChange() {
        word.learned(NOW);
        word.roundAnswer(true);
        word.roundAnswer(true);
        SetStep late = word.roundAnswer(false);
        assertThat(late).isEqualTo(new SetStep(2, WordState.MASTERED, false));
        assertThat(word.rounds()).isEqualTo(2);
    }

    @Test
    void itCanPointToAnotherCard() {
        word.pointTo(new WordId(9));
        assertThat(word.word()).isEqualTo(new WordId(9));
    }
}
