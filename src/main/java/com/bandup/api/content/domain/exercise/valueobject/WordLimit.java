package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Arrays;
import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * A gap's word limit, e.g. "NO MORE THAN TWO WORDS AND/OR A NUMBER" =
 * {@code (2, true)}: with {@code numbersAllowed}, numbers don't count as words.
 * Same rule as Practice's grading (each context keeps its own copy).
 */
@ValueObject
public record WordLimit(int maxWords, boolean numbersAllowed) {

    private static final Pattern NUMBER = Pattern.compile("\\d+([.,:/-]\\d+)*");

    public WordLimit {
        if (maxWords < 1) {
            throw DomainException.of("maxWords", "The word limit must be 1 or more.");
        }
    }

    public boolean allows(String answer) {
        return Arrays.stream(answer.strip().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .filter(word -> !(numbersAllowed && NUMBER.matcher(word).matches()))
                .count() <= maxWords;
    }
}
