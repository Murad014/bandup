package com.bandup.api.shared.application;

import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.language.LanguageFallbacks;

/**
 * Port: the known languages and their fallbacks (the {@code language} table).
 * Use cases call {@link #fallbacks()} once per request and build every
 * {@link com.bandup.api.shared.domain.language.LanguageChain} they need from it.
 */
public interface LanguageCatalog {

    LanguageFallbacks fallbacks();
}
