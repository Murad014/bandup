package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.Arrays;
import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * A gap's word limit, e.g. "NO MORE THAN TWO WORDS AND/OR A NUMBER" =
 * {@code (2, true)}: with {@code numbersAllowed}, numbers ("17", "1995",
 * "3.5") don't count towards the limit.
 */
@ValueObject
public record WordLimit(int maxWords, boolean numbersAllowed) {

    private static final Pattern NUMBER = Pattern.compile("\\d+([.,:/-]\\d+)*");

    public WordLimit {
        if (maxWords < 1) {
            throw new IllegalArgumentException("A word limit is at least 1, got " + maxWords);
        }
    }

    public boolean allows(String answer) {
        long words = Arrays.stream(answer.strip().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .filter(word -> !(numbersAllowed && NUMBER.matcher(word).matches()))
                .count();
        return words <= maxWords;
    }
}
