package com.bandup.api.practice.domain.attempt.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class GradingRuleTest {

    @Test
    void chooseNNeedsExactlyNKeysAndIsWorthNPoints() {
        GradingRule rule = GradingRule.mcMulti(List.of("A", "D"), 2);

        assertThat(rule.points()).isEqualTo(2);
        assertThatThrownBy(() -> GradingRule.mcMulti(List.of("A", "D", "E"), 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GradingRule.mcMulti(List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyOtherFamilyIsWorthOnePoint() {
        assertThat(GradingRule.choiceFixed("TRUE").points()).isEqualTo(1);
        assertThat(GradingRule.gap(List.of("elk"), new WordLimit(1, false)).points()).isEqualTo(1);
        assertThat(GradingRule.mcSingle("B").points()).isEqualTo(1);
        assertThat(GradingRule.match("iv").points()).isEqualTo(1);
    }

    @Test
    void rejectsMissingBlankOrRepeatedAnswers() {
        assertThatThrownBy(() -> GradingRule.gap(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GradingRule.choiceFixed(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GradingRule.gap(List.of("Elk", "elk "), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsTheAcceptedValuesAsStoredForTheSnapshot() {
        assertThat(GradingRule.gap(List.of("River banks", "riverbanks"), null).accepted())
                .containsExactly("River banks", "riverbanks");
    }

    @Test
    void wordLimitCountsWordsAndOptionallySkipsNumbers() {
        assertThat(new WordLimit(2, false).allows("grey  wolves")).isTrue();
        assertThat(new WordLimit(2, false).allows("17 grey wolves")).isFalse();
        assertThat(new WordLimit(2, true).allows("17 grey wolves")).isTrue();
        assertThat(new WordLimit(2, true).allows("1,200 10:30 grey wolves")).isTrue();
        assertThat(new WordLimit(1, true).allows("17th century")).isFalse(); // "17th" is a word
        assertThatThrownBy(() -> new WordLimit(0, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void familiesMapToTheStoredCodes() {
        assertThat(QuestionFamily.ofCode("mcMulti")).isEqualTo(QuestionFamily.MC_MULTI);
        assertThat(QuestionFamily.CHOICE_FIXED.code()).isEqualTo("choiceFixed");
        assertThatThrownBy(() -> QuestionFamily.ofCode("essay")).isInstanceOf(IllegalArgumentException.class);
    }
}
