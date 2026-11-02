package com.bandup.api.grammar.domain.lesson.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TileShufflerTest {

    private final TileShuffler shuffler = new TileShuffler(new AnswerNormaliser());

    @Test
    void neverReturnsTheAnswerOrderAndKeepsEveryTile() {
        List<String> tiles = List.of("She", "has", "just", "left");
        for (long seed = 0; seed < 500; seed++) {
            List<String> shuffled = shuffler.shuffle(tiles, "She has just left", seed);
            assertThat(String.join(" ", shuffled)).isNotEqualTo("She has just left");
            assertThat(shuffled).containsExactlyInAnyOrderElementsOf(tiles);
        }
    }

    @Test
    void theSameSeedGivesTheSameOrder() {
        List<String> tiles = List.of("since", "We", "him", "have", "2019", "known");
        assertThat(shuffler.shuffle(tiles, "We have known him since 2019", 42))
                .isEqualTo(shuffler.shuffle(tiles, "We have known him since 2019", 42));
    }

    @Test
    void twoTilesAreSwapped() {
        assertThat(shuffler.shuffle(List.of("If", "only"), "If only", 7)).containsExactly("only", "If");
    }
}
