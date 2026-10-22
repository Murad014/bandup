package com.bandup.api.shared.domain.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageFallbacksTest {

    /** Same shape as the seeded languages, plus Portuguese for a regional chain. */
    private static final LanguageFallbacks FALLBACKS = fallbacks(
            "en", null,
            "az", "en",
            "tr", "en",
            "ru", "en",
            "kk", "ru",
            "pt", "en",
            "pt-BR", "pt");

    @Test
    void exactHitUsesTheRequestedLanguage() {
        LanguageChain az = FALLBACKS.chainFor("az");
        assertThat(codes(az)).containsExactly("az", "en");
        assertThat(az.pick(translations("az", "məhv edilmək", "en", "removed completely"))).contains("məhv edilmək");
    }

    @Test
    void missingTranslationFallsBackToEnglish() {
        assertThat(FALLBACKS.chainFor("tr").pick(translations("az", "maral", "en", "a very large kind of deer")))
                .contains("a very large kind of deer");
    }

    @Test
    void itemMissingInEveryChainLanguageIsEmpty() {
        assertThat(FALLBACKS.chainFor("tr").pick(translations("az", "dövlət tərəfindən dəstəklənən"))).isEmpty();
    }

    @Test
    void regionalLanguageFallsBackThroughItsBaseLanguage() {
        LanguageChain ptBr = FALLBACKS.chainFor("pt-BR");
        assertThat(codes(ptBr)).containsExactly("pt-BR", "pt", "en");
        assertThat(ptBr.pick(translations("pt", "eliminados", "en", "removed completely"))).contains("eliminados");
        assertThat(ptBr.pick(translations("pt-BR", "eliminados (BR)", "pt", "eliminados"))).contains("eliminados (BR)");
    }

    @Test
    void followsLongerChains() {
        assertThat(codes(FALLBACKS.chainFor("kk"))).containsExactly("kk", "ru", "en");
        assertThat(FALLBACKS.chainFor("kk").pick(translations("ru", "уничтожены", "en", "removed"))).contains("уничтожены");
    }

    @Test
    void matchesCodesCaseInsensitivelyAndUsesTheStoredSpelling() {
        assertThat(codes(FALLBACKS.chainFor("PT-br"))).containsExactly("pt-BR", "pt", "en");
        assertThat(codes(FALLBACKS.chainFor(" AZ "))).containsExactly("az", "en");
    }

    @Test
    void unknownRegionUsesTheBaseLanguage() {
        assertThat(codes(FALLBACKS.chainFor("az-AZ"))).containsExactly("az", "en");
        assertThat(codes(FALLBACKS.chainFor("pt-PT"))).containsExactly("pt", "en");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "xx", "xx-YY", "klingon", "en_US", "not a code!"})
    void unknownMalformedOrMissingLanguageGetsEnglishOnly(String requested) {
        assertThat(codes(FALLBACKS.chainFor(requested))).containsExactly("en");
    }

    @Test
    void englishItselfIsJustEnglish() {
        assertThat(codes(FALLBACKS.chainFor("en"))).containsExactly("en");
        assertThat(codes(FALLBACKS.chainFor("en-GB"))).containsExactly("en");
    }

    @Test
    void everyChainEndsWithEnglishEvenIfTheDataForgetsIt() {
        LanguageFallbacks noFallback = fallbacks("en", null, "fr", null);
        assertThat(codes(noFallback.chainFor("fr"))).containsExactly("fr", "en");
    }

    @Test
    void theWalkStopsAtEnglishEvenIfEnglishWronglyHasAFallback() {
        LanguageFallbacks odd = fallbacks("en", "az", "az", "en");
        assertThat(codes(odd.chainFor("az"))).containsExactly("az", "en");
        assertThat(codes(odd.chainFor("en"))).containsExactly("en");
    }

    @Test
    void aLoopInTheDataStops() {
        LanguageFallbacks loop = fallbacks("en", null, "aa", "bb", "bb", "aa");
        assertThat(codes(loop.chainFor("aa"))).containsExactly("aa", "bb", "en");
    }

    @Test
    void primaryIsTheLanguageActuallyUsed() {
        assertThat(FALLBACKS.chainFor("az-AZ").primary().value()).isEqualTo("az");
        assertThat(FALLBACKS.chainFor("xx").primary()).isEqualTo(LanguageCode.ENGLISH);
    }

    @Test
    void knowsWhichLanguagesExistAndTheirStoredSpelling() {
        assertThat(FALLBACKS.isKnown(LanguageCode.of("AZ"))).isTrue();
        assertThat(FALLBACKS.isKnown(LanguageCode.of("az-AZ"))).isFalse(); // regional fallback is only for chains
        assertThat(FALLBACKS.isKnown(LanguageCode.of("xx"))).isFalse();
        assertThat(FALLBACKS.storedSpelling(LanguageCode.of("PT-br")).map(LanguageCode::value)).contains("pt-BR");
        assertThat(FALLBACKS.storedSpelling(LanguageCode.of("xx"))).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    private static LanguageFallbacks fallbacks(String... codeThenFallback) {
        Map<LanguageCode, Optional<LanguageCode>> map = new HashMap<>();
        for (int i = 0; i < codeThenFallback.length; i += 2) {
            String fallback = codeThenFallback[i + 1];
            map.put(LanguageCode.of(codeThenFallback[i]), Optional.ofNullable(fallback).map(LanguageCode::of));
        }
        return LanguageFallbacks.of(map);
    }

    private static Translations<String> translations(String... codeThenValue) {
        Translations<String> t = Translations.empty();
        for (int i = 0; i < codeThenValue.length; i += 2) {
            t = t.with(LanguageCode.of(codeThenValue[i]), codeThenValue[i + 1]);
        }
        return t;
    }

    private static List<String> codes(LanguageChain chain) {
        return chain.codes().stream().map(LanguageCode::value).toList();
    }
}
