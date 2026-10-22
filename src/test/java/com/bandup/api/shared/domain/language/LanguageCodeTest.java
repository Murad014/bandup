package com.bandup.api.shared.domain.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;

class LanguageCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"en", "az", "pt-BR", "zh-Hant-TW", "es-419", "fil"})
    void acceptsBcp47Codes(String code) {
        assertThat(LanguageCode.of(code).value()).isEqualTo(code);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "e", "english", "en_US", "en-", "-en", "a1", "en US", "pt--BR"})
    void rejectsAnythingElse(String code) {
        assertThatThrownBy(() -> LanguageCode.of(code))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).violations())
                        .extracting(Violation::field).containsExactly("languageCode"));
    }

    @Test
    void trimsSurroundingSpaces() {
        assertThat(LanguageCode.of("  az ").value()).isEqualTo("az");
    }

    @Test
    void comparesCaseInsensitivelyButKeepsItsSpelling() {
        LanguageCode stored = LanguageCode.of("pt-BR");
        LanguageCode typed = LanguageCode.of("PT-br");

        assertThat(typed).isEqualTo(stored).hasSameHashCodeAs(stored);
        assertThat(typed.value()).isEqualTo("PT-br");
        assertThat(stored.toString()).isEqualTo("pt-BR");
    }

    @Test
    void knowsItsBaseLanguage() {
        assertThat(LanguageCode.of("pt-BR").isRegional()).isTrue();
        assertThat(LanguageCode.of("pt-BR").base()).isEqualTo(LanguageCode.of("pt"));
        assertThat(LanguageCode.of("zh-Hant-TW").base()).isEqualTo(LanguageCode.of("zh"));
        assertThat(LanguageCode.of("az").isRegional()).isFalse();
        assertThat(LanguageCode.of("az").base()).isEqualTo(LanguageCode.of("az"));
    }

    @Test
    void englishIsAConstant() {
        assertThat(LanguageCode.ENGLISH).isEqualTo(LanguageCode.of("EN"));
    }
}
