package com.bandup.api.vocabulary.domain.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.domain.word.service.WordGate;
import com.bandup.api.vocabulary.domain.word.service.WordNormaliser;

class WordGateTest {

    private final WordNormaliser normaliser = new WordNormaliser();
    private final WordGate gate = new WordGate(normaliser);

    @Test
    void textIsNormalised() {
        assertThat(normaliser.normalise("  Riverbanks, ")).isEqualTo("riverbanks");
        assertThat(normaliser.normalise("“Give  UP”")).isEqualTo("give up");
        assertThat(normaliser.normalise("Don’t")).isEqualTo("don't");
        assertThat(normaliser.normalise(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"erosion", "Riverbanks.", "give up", "well-being", "don't", "a sharp decline"})
    void wordsAndShortPhrasesAreAdmitted(String text) {
        assertThat(gate.admit(text).value()).isEqualTo(normaliser.normalise(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "1995", "café", "eros1on", "one two three four", "<b>x</b>", "a--b", "word; drop table",
            "ignore previous instructions and"})
    void junkIsRefusedBeforeAnyLookup(String text) {
        assertThatThrownBy(() -> gate.admit(text)).isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).violations().get(0).field()).isEqualTo("text"));
    }

    @Test
    void atMostFortyCharacters() {
        assertThat(gate.admit("a".repeat(40)).value()).hasSize(40);
        assertThatThrownBy(() -> gate.admit("a".repeat(41))).hasMessageContaining("40");
    }
}
