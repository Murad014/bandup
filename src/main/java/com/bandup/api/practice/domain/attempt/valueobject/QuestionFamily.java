package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.Arrays;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** How a question type is answered and scored (database design §4.6). */
@ValueObject
public enum QuestionFamily {
    /** TFNG, YNNG: one fixed value. */
    CHOICE_FIXED("choiceFixed"),
    /** FILL, SUMMARY, SHORT, DIAGRAM_LABEL: free text, one of several accepted values, word limit. */
    GAP("gap"),
    /** MCQ: one option key. */
    MC_SINGLE("mcSingle"),
    /** MCMA: "Choose N" option keys, 1 point each. */
    MC_MULTI("mcMulti"),
    /** Matching types and SUMMARY_BOX: one key from the group's option bank. */
    MATCH("match");

    private final String code;

    QuestionFamily(String code) {
        this.code = code;
    }

    /** The code stored in {@code question_type.family}. */
    public String code() {
        return code;
    }

    public static QuestionFamily ofCode(String code) {
        return Arrays.stream(values())
                .filter(f -> f.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown question family " + code));
    }
}
