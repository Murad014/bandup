package com.bandup.api.grammar.domain.lesson.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnswerNormaliserTest {

    private final AnswerNormaliser normaliser = new AnswerNormaliser();

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "  Haven’t  seen. |haven't seen",
            "WON|won",
            "had studied!?|had studied",
            "it's ok , |it's ok",
            "‘quoted’|'quoted'",
            "   |\"\"",
            "a.b|a.b"})
    void makesTypedAnswersComparable(String typed, String expected) {
        assertThat(normaliser.normalise(typed)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"I saw him|I saw", "an|a", "won't|wont"})
    void keepsDifferentAnswersDifferent(String one, String other) {
        assertThat(normaliser.normalise(one)).isNotEqualTo(normaliser.normalise(other));
    }
}
