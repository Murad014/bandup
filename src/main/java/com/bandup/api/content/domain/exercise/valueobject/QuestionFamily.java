package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Arrays;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** How a question type is authored and answered ({@code question_type.family}). */
@ValueObject
public enum QuestionFamily {
    /** TFNG, YNNG: one value from a fixed set. */
    CHOICE_FIXED("choiceFixed"),
    /** Completion and short answers: accepted alternatives, word limit. */
    GAP("gap"),
    /** MCQ: own options, one key. */
    MC_SINGLE("mcSingle"),
    /** MCMA: own options, "Choose N" keys, N points. */
    MC_MULTI("mcMulti"),
    /** Matching types and SUMMARY_BOX: one key from the group's bank. */
    MATCH("match");

    private final String code;

    QuestionFamily(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static QuestionFamily ofCode(String code) {
        return Arrays.stream(values()).filter(f -> f.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown question family " + code));
    }

    /** Families whose questions have their own options. */
    public boolean hasQuestionOptions() {
        return this == MC_SINGLE || this == MC_MULTI;
    }
}
