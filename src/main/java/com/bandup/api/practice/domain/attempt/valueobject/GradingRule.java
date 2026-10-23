package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import lombok.Getter;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * How one question is scored: its family, the accepted values (database design
 * §4.6) and its group's rules. Built only through the per-family factories, so
 * an impossible rule (e.g. "Choose TWO" with three keys) can't exist.
 */
@ValueObject
public final class GradingRule {

    @Getter
    private final QuestionFamily family;
    /** The accepted values (gap) or required keys, as stored: the attempt's answer snapshot. */
    @Getter
    private final List<String> accepted;
    /** How many keys a "Choose N" question needs; 1 for every other family. */
    @Getter
    private final int chooseCount;
    private final WordLimit wordLimit;

    private GradingRule(QuestionFamily family, List<String> accepted, int chooseCount, WordLimit wordLimit) {
        this.family = Objects.requireNonNull(family, "family");
        this.accepted = List.copyOf(accepted);
        if (this.accepted.isEmpty() || this.accepted.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("A question needs accepted answers that aren't blank, got " + accepted);
        }
        if (this.accepted.stream().map(a -> a.strip().toLowerCase(Locale.ROOT)).distinct().count() != this.accepted.size()) {
            throw new IllegalArgumentException("Accepted answers repeat: " + accepted);
        }
        this.chooseCount = chooseCount;
        this.wordLimit = wordLimit;
    }

    /** TFNG / YNNG: exactly one value. */
    public static GradingRule choiceFixed(String value) {
        return new GradingRule(QuestionFamily.CHOICE_FIXED, List.of(value), 1, null);
    }

    /** Gap: one or more accepted alternatives; {@code wordLimit} may be null (no limit). */
    public static GradingRule gap(List<String> accepted, WordLimit wordLimit) {
        return new GradingRule(QuestionFamily.GAP, accepted, 1, wordLimit);
    }

    /** MCQ: exactly one option key. */
    public static GradingRule mcSingle(String key) {
        return new GradingRule(QuestionFamily.MC_SINGLE, List.of(key), 1, null);
    }

    /** "Choose N": exactly N required keys; worth N points. */
    public static GradingRule mcMulti(List<String> keys, int chooseCount) {
        if (chooseCount < 1 || keys.size() != chooseCount) {
            throw new IllegalArgumentException("\"Choose " + chooseCount + "\" needs exactly " + chooseCount + " keys, got " + keys);
        }
        return new GradingRule(QuestionFamily.MC_MULTI, keys, chooseCount, null);
    }

    /** Matching / summary box: exactly one key from the group's bank. */
    public static GradingRule match(String key) {
        return new GradingRule(QuestionFamily.MATCH, List.of(key), 1, null);
    }

    public Optional<WordLimit> wordLimit() {
        return Optional.ofNullable(wordLimit);
    }

    /** Points the question is worth, and so how many numbers it takes. */
    public int points() {
        return chooseCount;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GradingRule r && family == r.family && accepted.equals(r.accepted)
                && chooseCount == r.chooseCount && Objects.equals(wordLimit, r.wordLimit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(family, accepted, chooseCount, wordLimit);
    }

    @Override
    public String toString() {
        return family + accepted.toString() + (wordLimit == null ? "" : " " + wordLimit);
    }
}
