package com.bandup.api.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.LanguageFallbacks;

/** The language adapter against the seeded {@code language} table. */
@JdbcTest
@Import({TestcontainersConfiguration.class, JdbcLanguageCatalog.class})
class JdbcLanguageCatalogTest {

    @Autowired
    JdbcLanguageCatalog catalog;

    @Test
    void buildsChainsFromTheSeededLanguages() {
        LanguageFallbacks fallbacks = catalog.fallbacks();

        assertThat(codes(fallbacks.chainFor("az"))).containsExactly("az", "en");
        assertThat(codes(fallbacks.chainFor("kk"))).containsExactly("kk", "ru", "en");
        assertThat(codes(fallbacks.chainFor("ar"))).containsExactly("ar", "en");
        assertThat(codes(fallbacks.chainFor("AZ-az"))).containsExactly("az", "en");
        assertThat(codes(fallbacks.chainFor("xx"))).containsExactly("en");
    }

    @Test
    void includesDisabledLanguages() {
        // tr is seeded but switched off; "enabled" only controls pickers, not resolution.
        assertThat(codes(catalog.fallbacks().chainFor("tr"))).containsExactly("tr", "en");
    }

    private static List<String> codes(LanguageChain chain) {
        return chain.codes().stream().map(LanguageCode::value).toList();
    }
}
