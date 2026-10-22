package com.bandup.api.shared.domain.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TranslationsTest {

    private static final LanguageCode AZ = LanguageCode.of("az");
    private static final LanguageCode EN = LanguageCode.ENGLISH;

    @Test
    void holdsOneValuePerLanguage() {
        Translations<String> elk = Translations.<String>empty()
                .with(AZ, "maral")
                .with(EN, "a very large kind of deer");

        assertThat(elk.get(AZ)).contains("maral");
        assertThat(elk.get(LanguageCode.of("tr"))).isEmpty();
        assertThat(elk.languages()).containsExactly(AZ, EN);
    }

    @Test
    void withReplacesTheValueEvenIfOnlyTheCaseOfTheCodeDiffers() {
        Translations<String> t = Translations.<String>empty()
                .with(LanguageCode.of("pt-BR"), "old")
                .with(LanguageCode.of("PT-br"), "new");

        assertThat(t.asMap()).hasSize(1);
        assertThat(t.get(LanguageCode.of("pt-BR"))).contains("new");
    }

    @Test
    void isImmutable() {
        Translations<String> original = Translations.<String>empty().with(AZ, "maral");
        Translations<String> changed = original.with(AZ, "başqa");

        assertThat(original.get(AZ)).contains("maral");
        assertThat(changed.get(AZ)).contains("başqa");
        assertThatThrownBy(() -> original.asMap().put(EN, "x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copiesTheMapItIsBuiltFrom() {
        Map<LanguageCode, String> source = new HashMap<>(Map.of(AZ, "maral"));
        Translations<String> t = Translations.of(source);
        source.put(EN, "deer");

        assertThat(t.languages()).containsExactly(AZ);
    }

    @Test
    void rejectsMissingValues() {
        assertThatThrownBy(() -> Translations.<String>empty().with(AZ, null)).isInstanceOf(NullPointerException.class);
        Map<LanguageCode, String> withNull = new HashMap<>();
        withNull.put(AZ, null);
        assertThatThrownBy(() -> Translations.of(withNull)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void comparesByContent() {
        assertThat(Translations.of(Map.of(AZ, "maral"))).isEqualTo(Translations.<String>empty().with(AZ, "maral"));
        assertThat(Translations.<String>empty().isEmpty()).isTrue();
    }
}
