package com.bandup.api.content.domain.exercise.valueobject;

import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/** An evidence span's id in the passage ({@code <span class="ev" id="e4">}). */
@ValueObject
public record EvidenceKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("e[0-9]{1,6}");

    public EvidenceKey {
        if (!isValid(value)) {
            throw DomainException.of("passageHtml", "Evidence id '" + value + "' must be 'e' followed by digits.");
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
