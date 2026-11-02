package com.bandup.api.grammar.domain.lesson.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import lombok.RequiredArgsConstructor;

import com.bandup.api.shared.domain.ddd.DomainService;

/**
 * Shuffles a {@code build} item's tiles (grammar requirement 5.7). The same
 * seed (a run's id) always gives the same order, so reloading a run doesn't
 * reshuffle; the result is never the answer order.
 */
@DomainService
@RequiredArgsConstructor
public final class TileShuffler {

    private final AnswerNormaliser normaliser;

    public List<String> shuffle(List<String> tiles, String answer, long seed) {
        List<String> shuffled = new ArrayList<>(tiles);
        Collections.shuffle(shuffled, new Random(seed));
        String target = normaliser.normalise(answer);
        for (int turn = 0; turn < shuffled.size() && inAnswerOrder(shuffled, target); turn++) {
            Collections.rotate(shuffled, 1);
        }
        return List.copyOf(shuffled);
    }

    private boolean inAnswerOrder(List<String> tiles, String target) {
        return normaliser.normalise(String.join(" ", tiles)).equals(target);
    }
}
