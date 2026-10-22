package com.bandup.api.identity.domain.user.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;

class EmailTest {

    @Test
    void isTrimmedAndLowerCased() {
        assertThat(Email.of("  Aysel.Mammadova@Example.COM ").value()).isEqualTo("aysel.mammadova@example.com");
    }

    @Test
    void sameAddressInAnyCaseIsEqual() {
        assertThat(Email.of("Murad@Example.com")).isEqualTo(Email.of("murad@example.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void isRequired(String raw) {
        assertThatThrownBy(() -> Email.of(raw))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).violations())
                        .containsExactly(new Violation("email", "Email is required.")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aysel", "aysel@", "@example.com", "aysel@example", "ay sel@example.com", "a@b@example.com"})
    void mustLookLikeAnEmail(String raw) {
        assertThatThrownBy(() -> Email.of(raw))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Enter a valid email address.");
    }

    @Test
    void mustNotBeLongerThan254Characters() {
        String longLocal = "a".repeat(250);
        assertThatThrownBy(() -> Email.of(longLocal + "@example.com")).isInstanceOf(DomainException.class);
    }

    @Test
    void constructorOnlyAcceptsNormalisedValues() {
        assertThatThrownBy(() -> new Email("Aysel@Example.com")).isInstanceOf(IllegalArgumentException.class);
    }
}
