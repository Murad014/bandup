package com.bandup.api.vocabulary.domain.saved.valueobject;

import java.util.Optional;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * Where a learner saved a word: a reading passage (its slug and the sentence
 * the word was in) or typed by hand (both empty).
 */
@ValueObject
public record WordSource(String exercise, String sentence) {

    public static final int MAX_SENTENCE = 400;

    public WordSource {
        exercise = exercise == null || exercise.isBlank() ? null : exercise.strip();
        sentence = sentence == null || sentence.isBlank() ? null : sentence.strip();
        if (sentence != null && sentence.length() > MAX_SENTENCE) {
            sentence = sentence.substring(0, MAX_SENTENCE);
        }
    }

    public static WordSource typed() {
        return new WordSource(null, null);
    }

    public Optional<String> exerciseSlug() {
        return Optional.ofNullable(exercise);
    }

    public Optional<String> sentenceText() {
        return Optional.ofNullable(sentence);
    }
}
