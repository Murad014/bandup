package com.bandup.api.content.domain.exercise.valueobject;

import java.util.List;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * A question's answer, in order: the accepted alternatives of a gap, the
 * fixed value (TFNG), or option key(s). Blank entries are dropped; a value
 * can't appear twice. May be empty in a draft.
 */
@ValueObject
public record AnswerKey(List<String> values) {

    public AnswerKey {
        values = values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).toList();
        if (values.stream().distinct().count() != values.size()) {
            throw DomainException.of("answer", "An answer is listed twice: " + values);
        }
    }

    public static AnswerKey none() {
        return new AnswerKey(List.of());
    }

    public static AnswerKey of(String... values) {
        return new AnswerKey(List.of(values));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
