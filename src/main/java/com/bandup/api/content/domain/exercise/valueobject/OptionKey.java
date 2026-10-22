package com.bandup.api.content.domain.exercise.valueobject;

import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/** An option's letter or numeral: {@code A}, {@code iv} (1–8 letters or digits). */
@ValueObject
public record OptionKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9]{1,8}");

    public OptionKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw DomainException.of("options", "Option key '" + value + "' must be 1–8 letters or digits.");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
