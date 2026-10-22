package com.bandup.api.shared.domain.language;

import java.util.Locale;
import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * A BCP-47 language code such as {@code en}, {@code az} or {@code pt-BR}.
 *
 * <p>Codes compare case-insensitively ({@code PT-br} equals {@code pt-BR}), as
 * BCP-47 requires, but keep the spelling they were created with, so the stored
 * form is what gets shown and saved.
 */
@ValueObject
public final class LanguageCode {

    /** Every fallback chain ends here. */
    public static final LanguageCode ENGLISH = new LanguageCode("en");

    /** Primary language (2–3 letters), then optional subtags: pt-BR, zh-Hant-TW, es-419. */
    private static final Pattern BCP47 = Pattern.compile("[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*");

    private final String value;

    private LanguageCode(String value) {
        this.value = value;
    }

    /** @throws DomainException if the code is blank or not BCP-47 shaped */
    public static LanguageCode of(String code) {
        String trimmed = code == null ? "" : code.trim();
        if (!BCP47.matcher(trimmed).matches()) {
            throw DomainException.of("languageCode", "Not a language code: '" + code + "'");
        }
        return new LanguageCode(trimmed);
    }

    /** The code as created, e.g. {@code pt-BR}. */
    public String value() {
        return value;
    }

    /** True for codes with a region or script, e.g. {@code pt-BR}. */
    public boolean isRegional() {
        return value.indexOf('-') > 0;
    }

    /** The primary language: {@code pt} for {@code pt-BR}; itself if not regional. */
    public LanguageCode base() {
        return isRegional() ? new LanguageCode(value.substring(0, value.indexOf('-'))) : this;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LanguageCode code && value.equalsIgnoreCase(code.value);
    }

    @Override
    public int hashCode() {
        return value.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
