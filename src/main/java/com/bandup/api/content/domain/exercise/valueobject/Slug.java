package com.bandup.api.content.domain.exercise.valueobject;

import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/** An exercise's public id in URLs ({@code /reading/yellowstone-wolves}): lower-case words joined by hyphens. */
@ValueObject
public record Slug(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public Slug {
        if (value == null || value.length() > 120 || !FORMAT.matcher(value).matches()) {
            throw DomainException.of("slug", "Use lower-case letters, digits and single hyphens (at most 120 characters).");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
