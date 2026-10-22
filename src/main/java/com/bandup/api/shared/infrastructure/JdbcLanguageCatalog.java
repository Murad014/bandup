package com.bandup.api.shared.infrastructure;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.LanguageFallbacks;

/**
 * {@link LanguageCatalog} backed by the {@code language} table. Reads every
 * language (enabled or not: "enabled" only controls what pickers offer) with
 * one small query per call.
 */
@Component
class JdbcLanguageCatalog implements LanguageCatalog {

    private final JdbcClient jdbc;

    JdbcLanguageCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LanguageFallbacks fallbacks() {
        Map<LanguageCode, Optional<LanguageCode>> languages = new HashMap<>();
        jdbc.sql("select code, fallback_code from language").query(rs -> {
            String fallback = rs.getString("fallback_code");
            languages.put(LanguageCode.of(rs.getString("code")),
                    fallback == null ? Optional.empty() : Optional.of(LanguageCode.of(fallback)));
        });
        return LanguageFallbacks.of(languages);
    }
}
