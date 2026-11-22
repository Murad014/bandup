package com.bandup.api.vocabulary.domain.saved.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bandup.api.shared.domain.ddd.DomainService;
import com.bandup.api.vocabulary.domain.saved.valueobject.SetMember;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSet;

/**
 * Cuts a learner's learned words (in learned order) into sets of {@value #SET_SIZE}
 * (vocabulary requirements 6.1, 6.2; decision 16). Sets aren't stored: they
 * are worked out from the words each time.
 */
@DomainService
public final class SetPlan {

    public static final int SET_SIZE = 25;

    /** @param learned the learned words, oldest learned first */
    public List<WordSet> split(List<SetMember> learned) {
        List<WordSet> sets = new ArrayList<>();
        for (int from = 0; from < learned.size(); from += SET_SIZE) {
            sets.add(new WordSet(sets.size() + 1, learned.subList(from, Math.min(from + SET_SIZE, learned.size()))));
        }
        return List.copyOf(sets);
    }

    /** The first set with a word not mastered; empty when there is none. */
    public Optional<WordSet> current(List<WordSet> sets) {
        return sets.stream().filter(s -> !s.isMastered()).findFirst();
    }
}
