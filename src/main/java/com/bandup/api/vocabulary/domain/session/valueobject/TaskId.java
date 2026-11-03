package com.bandup.api.vocabulary.domain.session.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A task within one session: {@code t1}, {@code t2}, … */
@ValueObject
public record TaskId(String value) {

    public TaskId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("t[1-9][0-9]{0,2}")) {
            throw new IllegalArgumentException("Invalid TaskId " + value);
        }
    }

    public static TaskId of(int number) {
        return new TaskId("t" + number);
    }
}
