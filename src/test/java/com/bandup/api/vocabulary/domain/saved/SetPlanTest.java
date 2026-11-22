package com.bandup.api.vocabulary.domain.saved;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bandup.api.vocabulary.domain.saved.service.SetPlan;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.saved.valueobject.SetMember;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSet;

/** Learned words → sets of 25, the current set, rounds (requirements 6.1, 6.2; decision 16). */
class SetPlanTest {

    private final SetPlan plan = new SetPlan();

    @Test
    void learnedWordsAreCutIntoSetsOf25InTheirOrder() {
        List<SetMember> words = words(54, 0);
        List<WordSet> sets = plan.split(words);
        assertThat(sets).extracting(WordSet::number).containsExactly(1, 2, 3);
        assertThat(sets).extracting(s -> s.members().size()).containsExactly(25, 25, 4);
        assertThat(sets.get(1).members().get(0)).isEqualTo(words.get(25));
        assertThat(plan.split(List.of())).isEmpty();
    }

    @Test
    void theCurrentSetIsTheFirstWithAWordNotMastered() {
        List<SetMember> words = words(54, 25);
        words.set(30, mastered(words.get(30)));
        List<WordSet> sets = plan.split(words);
        assertThat(plan.current(sets)).map(WordSet::number).contains(2);
        assertThat(sets.get(0).isMastered()).isTrue();
        assertThat(sets.get(1).mastered()).isEqualTo(1);
        assertThat(plan.current(plan.split(words(30, 30)))).isEmpty();
    }

    @Test
    void aRoundAsksTheReadyWordsNotMasteredAndCountsRounds() {
        List<SetMember> words = new ArrayList<>(words(4, 1));
        words.set(1, new SetMember(words.get(1).saved(), 1, false, 3, true));
        words.set(2, new SetMember(words.get(2).saved(), 0, false, 1, false));
        WordSet set = plan.split(words).get(0);
        assertThat(set.toAsk()).containsExactly(words.get(1), words.get(3));
        assertThat(set.rounds()).isEqualTo(3);
    }

    /** {@code n} words; the first {@code masteredFirst} are mastered. */
    private static List<SetMember> words(int n, int masteredFirst) {
        List<SetMember> words = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SetMember m = new SetMember(new SavedWordId(UUID.randomUUID()), 0, false, 0, true);
            words.add(i < masteredFirst ? mastered(m) : m);
        }
        return words;
    }

    private static SetMember mastered(SetMember m) {
        return new SetMember(m.saved(), 2, true, 2, true);
    }
}
