package com.bandup.api.identity.domain.user.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.bandup.api.shared.domain.error.DomainException;

class NewPasswordTest {

    @Test
    void needsAtLeastTenCharacters() {
        assertThatThrownBy(() -> NewPassword.of("123456789"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("at least 10 characters");
        assertThatThrownBy(() -> NewPassword.of(null)).isInstanceOf(DomainException.class);
        assertThat(NewPassword.of("1234567890").reveal()).isEqualTo("1234567890");
    }

    @Test
    void countsCharactersNotBytes() {
        // 10 Azerbaijani letters are 20 bytes but only 10 characters: allowed.
        assertThat(NewPassword.of("əəəəəəəəəə").reveal()).hasSize(10);
        assertThatThrownBy(() -> NewPassword.of("əəəəəəəəə")).isInstanceOf(DomainException.class);
    }

    @Test
    void isAtMost72BytesBecauseBcryptIgnoresTheRest() {
        assertThat(NewPassword.of("a".repeat(72)).reveal()).hasSize(72);
        assertThatThrownBy(() -> NewPassword.of("a".repeat(73)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("at most 72 bytes");
        // 37 "ə" = 74 bytes.
        assertThatThrownBy(() -> NewPassword.of("ə".repeat(37))).isInstanceOf(DomainException.class);
    }

    @Test
    void isNeverPrinted() {
        assertThat(NewPassword.of("correct horse battery").toString()).doesNotContain("horse");
    }
}
