package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/** A lesson's public id in URLs ({@code /grammar/lessons/b1-present-perfect-1}): lower-case words joined by hyphens. */
@ValueObject
public record LessonSlug(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public LessonSlug {
        if (value == null || value.length() < 3 || value.length() > 80 || !FORMAT.matcher(value).matches()) {
            throw DomainException.of("id", "Use 3 to 80 lower-case letters, digits and single hyphens.");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
