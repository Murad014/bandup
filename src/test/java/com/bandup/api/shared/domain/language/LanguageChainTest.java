package com.bandup.api.shared.domain.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LanguageChainTest {

    private static final LanguageCode AZ = LanguageCode.of("az");
    private static final LanguageCode TR = LanguageCode.of("tr");
    private static final LanguageCode EN = LanguageCode.ENGLISH;

    @Test
    void mustEndWithEnglishOnceAndNotBeEmpty() {
        assertThat(LanguageChain.of(List.of(TR, EN)).primary()).isEqualTo(TR);
        assertThatThrownBy(() -> LanguageChain.of(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LanguageChain.of(List.of(TR))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LanguageChain.of(List.of(TR, EN, TR, EN))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LanguageChain.of(List.of(EN, LanguageCode.of("EN")))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void picksTheFirstLanguageThatHasAValue() {
        LanguageChain chain = LanguageChain.of(List.of(TR, EN));

        assertThat(chain.pick(Translations.of(Map.of(TR, "yırtıcı", EN, "a hunter")))).contains("yırtıcı");
        assertThat(chain.pick(Translations.of(Map.of(AZ, "maral", EN, "a very large kind of deer"))))
                .contains("a very large kind of deer");
        assertThat(chain.pick(Translations.of(Map.of(AZ, "dövlət tərəfindən dəstəklənən")))).isEmpty();
        assertThat(chain.pick(Translations.empty())).isEmpty();
    }

    @Test
    void pickAllResolvesEachItemOnItsOwnAndKeepsOrder() {
        // The guide's example: Emre (tr) opens the Wolves passage.
        Map<String, Translations<String>> glosses = new LinkedHashMap<>();
        glosses.put("g1", Translations.of(Map.of(AZ, "məhv edilmək", EN, "removed completely", TR, "yok edilmiş")));
        glosses.put("g2", Translations.of(Map.of(AZ, "dövlət tərəfindən dəstəklənən")));
        glosses.put("g3", Translations.of(Map.of(AZ, "yırtıcı", TR, "yırtıcı hayvan")));
        glosses.put("g4", Translations.of(Map.of(AZ, "maral", EN, "a very large kind of deer")));

        Map<String, String> shown = LanguageChain.of(List.of(TR, EN)).pickAll(glosses);

        assertThat(shown).containsExactly(
                Map.entry("g1", "yok edilmiş"),
                Map.entry("g3", "yırtıcı hayvan"),
                Map.entry("g4", "a very large kind of deer"));
    }

    @Test
    void englishOnlyChain() {
        assertThat(LanguageChain.englishOnly().codes()).containsExactly(EN);
    }
}
