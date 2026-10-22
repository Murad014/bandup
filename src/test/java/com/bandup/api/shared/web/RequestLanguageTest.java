package com.bandup.api.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class RequestLanguageTest {

    @Test
    void explicitLanguageWins() {
        assertThat(RequestLanguage.pick(" tr ", Optional.of("az"), "ru")).isEqualTo("tr");
    }

    @Test
    void thenTheSignedInUsersLanguage() {
        assertThat(RequestLanguage.pick(null, Optional.of("az"), "ru")).isEqualTo("az");
        assertThat(RequestLanguage.pick("  ", Optional.of("az"), "ru")).isEqualTo("az");
    }

    @Test
    void thenTheBrowsersMostPreferredLanguage() {
        assertThat(RequestLanguage.pick(null, Optional.empty(), "az-AZ,en;q=0.8")).isEqualToIgnoringCase("az-AZ");
        assertThat(RequestLanguage.pick(null, Optional.empty(), "en;q=0.5, tr;q=0.9")).isEqualTo("tr");
        assertThat(RequestLanguage.pick(null, Optional.of(" "), "ru")).isEqualTo("ru");
    }

    @Test
    void wildcardIsSkipped() {
        assertThat(RequestLanguage.pick(null, Optional.empty(), "*, az;q=0.5")).isEqualTo("az");
        assertThat(RequestLanguage.pick(null, Optional.empty(), "*")).isNull();
    }

    @Test
    void nothingUsableMeansNoneSoEnglish() {
        assertThat(RequestLanguage.pick(null, Optional.empty(), null)).isNull();
        assertThat(RequestLanguage.pick(null, Optional.empty(), "")).isNull();
        assertThat(RequestLanguage.pick(null, Optional.empty(), ";;;q=garbage")).isNull();
    }
}
