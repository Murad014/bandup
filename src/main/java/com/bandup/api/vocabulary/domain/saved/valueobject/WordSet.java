package com.bandup.api.vocabulary.domain.saved.valueobject;

import java.util.List;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * One set: up to 25 learned words in learned order (vocabulary requirement 6.1).
 *
 * @param number 1-based
 */
@ValueObject
public record WordSet(int number, List<SetMember> members) {

    public WordSet {
        members = List.copyOf(members);
    }

    public int mastered() {
        return (int) members.stream().filter(SetMember::mastered).count();
    }

    public boolean isMastered() {
        return mastered() == members.size();
    }

    /** Rounds played on this set: the most rounds any of its words was asked in. */
    public int rounds() {
        return members.stream().mapToInt(SetMember::rounds).max().orElse(0);
    }

    /** The words the next round asks: not mastered, with a READY card. */
    public List<SetMember> toAsk() {
        return members.stream().filter(m -> !m.mastered() && m.ready()).toList();
    }
}
