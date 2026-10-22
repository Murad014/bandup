package com.bandup.api.content.domain.exercise.valueobject;

import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/** A glossed word's key in the passage ({@code data-g="g7"}), allocated by the admin editor. */
@ValueObject
public record GlossKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("g[0-9]{1,6}");

    public GlossKey {
        if (!isValid(value)) {
            throw DomainException.of("passageHtml", "Gloss key '" + value + "' must be 'g' followed by digits.");
        }
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
