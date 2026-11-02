package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.Map;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

/**
 * A learner-facing text in one or more languages ({@code {"en": …, "az": …}};
 * grammar requirement 11). English is the required one; the {@code LessonPolicy}
 * checks it is there and not blank.
 */
@ValueObject
public record LessonText(Translations<String> values) {

    public LessonText {
        Objects.requireNonNull(values, "values");
    }

    public static LessonText english(String text) {
        return new LessonText(Translations.of(Map.of(LanguageCode.ENGLISH, text)));
    }

    /** The English text, or empty when there is none. */
    public String inEnglish() {
        return values.get(LanguageCode.ENGLISH).orElse("");
    }

    /** The text in the first chain language that has it (the chain always ends with English). */
    public String in(LanguageChain chain) {
        return chain.pick(values).orElse("");
    }
}
