package com.bandup.api.shared.domain.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class QuestionNumberingTest {

    @Test
    void numbersFollowOrderAndPoints() {
        // Q1–6 one point each, then a "Choose TWO" (Q7–8), then Q9.
        List<NumberRange> ranges = QuestionNumbering.number(List.of(1, 1, 1, 1, 1, 1, 2, 1));

        assertThat(ranges).extracting(NumberRange::toString)
                .containsExactly("1", "2", "3", "4", "5", "6", "7–8", "9");
        assertThat(ranges.get(6).size()).isEqualTo(2);
        assertThat(ranges.get(6).contains(8)).isTrue();
        assertThat(ranges.get(6).contains(9)).isFalse();
    }

    @Test
    void noQuestionsMeansNoNumbers() {
        assertThat(QuestionNumbering.number(List.of())).isEmpty();
    }

    @Test
    void rejectsQuestionsWorthNothing() {
        assertThatThrownBy(() -> QuestionNumbering.number(List.of(1, 0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangeMustStartAtOneOrLaterAndNotRunBackwards() {
        assertThatThrownBy(() -> new NumberRange(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NumberRange(5, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
