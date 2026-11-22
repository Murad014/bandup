package com.bandup.api.vocabulary.domain.saved.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * One learned word as the sets see it (vocabulary requirement 6).
 *
 * @param streak   rounds in a row with a right first try
 * @param mastered MASTERED
 * @param rounds   rounds it was asked in
 * @param ready    its card is READY (a round can ask it)
 */
@ValueObject
public record SetMember(SavedWordId saved, int streak, boolean mastered, int rounds, boolean ready) {

    public SetMember {
        Objects.requireNonNull(saved, "saved");
    }
}
