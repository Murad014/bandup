package com.bandup.api.vocabulary.domain.word.service;

import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;

import com.bandup.api.shared.domain.ddd.DomainService;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

/**
 * The input rules for a word a learner saves (vocabulary requirement 2.2):
 * 1–3 English words of letters, {@code '} and {@code -}, at most 40
 * characters. Checked before any lookup, so junk never reaches the database
 * or the AI.
 */
@DomainService
@RequiredArgsConstructor
public final class WordGate {

    /** One to three words; a word is letters, joined by single ' or -. */
    public static final Pattern WORDS = Pattern.compile("[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){0,2}");
    public static final int MAX_LENGTH = 40;

    private final WordNormaliser normaliser;

    /** @throws DomainException (field {@code text}) when the text can't be a word to learn */
    public WordForm admit(String text) {
        String form = normaliser.normalise(text);
        if (form.isEmpty()) {
            throw DomainException.of("text", "Write a word.");
        }
        if (form.length() > MAX_LENGTH) {
            throw DomainException.of("text", "A word or phrase is at most " + MAX_LENGTH + " characters.");
        }
        if (!WORDS.matcher(form).matches()) {
            throw DomainException.of("text", "Use 1 to 3 English words (letters, ' and - only).");
        }
        return new WordForm(form);
    }

    public boolean isWord(String normalised) {
        return normalised != null && normalised.length() <= MAX_LENGTH && WORDS.matcher(normalised).matches();
    }
}
