package com.bandup.api.vocabulary.domain.saved.aggregate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.shared.domain.ddd.AggregateRoot;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.saved.valueobject.SetStep;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSource;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordState;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * A word one learner wants to learn (aggregate root; vocabulary requirements
 * 4–6): a link to the shared card plus the learner's state. NEW until a learn
 * session teaches it, then LEARNING in the sets, and MASTERED after the first
 * try of {@value #ROUNDS_TO_MASTER} rounds in a row was right. A miss starts
 * it again at 0.
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class SavedWord {

    /** Rounds in a row with a right first try that master a word (requirement 6.6). */
    public static final int ROUNDS_TO_MASTER = 2;

    @EqualsAndHashCode.Include
    @Getter
    private final SavedWordId id;
    @Getter
    private final LearnerId learner;
    @Getter
    private WordId word;
    @Getter
    private final WordSource source;
    @Getter
    private final LocalDate addedOn;
    @Getter
    private WordState state;
    private Instant learnedAt;
    /** Rounds in a row the first try was right (0 to {@link #ROUNDS_TO_MASTER}). */
    @Getter
    private int streak;
    /** Rounds the word was asked in. */
    @Getter
    private int rounds;

    private SavedWord(SavedWordId id, LearnerId learner, WordId word, WordSource source, LocalDate addedOn,
            WordState state, Instant learnedAt, int streak, int rounds) {
        this.id = Objects.requireNonNull(id, "id");
        this.learner = Objects.requireNonNull(learner, "learner");
        this.word = Objects.requireNonNull(word, "word");
        this.source = Objects.requireNonNull(source, "source");
        this.addedOn = Objects.requireNonNull(addedOn, "addedOn");
        this.state = Objects.requireNonNull(state, "state");
        if (streak < 0 || streak > ROUNDS_TO_MASTER || rounds < 0) {
            throw new IllegalArgumentException("A streak is 0 to " + ROUNDS_TO_MASTER + ", rounds at least 0");
        }
        this.learnedAt = learnedAt;
        this.streak = streak;
        this.rounds = rounds;
    }

    /** A newly saved word (vocabulary requirements 1, 2). */
    public static SavedWord save(LearnerId learner, WordId word, WordSource source, LocalDate today) {
        return new SavedWord(SavedWordId.random(), learner, word, source, today, WordState.NEW, null, 0, 0);
    }

    public static SavedWord restore(SavedWordId id, LearnerId learner, WordId word, WordSource source,
            LocalDate addedOn, WordState state, Instant learnedAt, int streak, int rounds) {
        return new SavedWord(id, learner, word, source, addedOn, state, learnedAt, streak, rounds);
    }

    /** A learn session taught it (requirement 5.7): it joins the sets. Already learned words stay as they are. */
    public void learned(Instant now) {
        if (state != WordState.NEW) {
            return;
        }
        state = WordState.LEARNING;
        learnedAt = Objects.requireNonNull(now, "now");
        streak = 0;
    }

    /**
     * The first answer of this word in a round (requirement 6.6): right → one more in a row, MASTERED at
     * {@value #ROUNDS_TO_MASTER}; wrong → back to 0. A MASTERED word (mastered in another tab) doesn't change.
     */
    public SetStep roundAnswer(boolean correct) {
        if (state == WordState.MASTERED) {
            return new SetStep(streak, state, false);
        }
        if (state != WordState.LEARNING) {
            throw new IllegalStateException("Only a learned word is asked in a round, not " + state);
        }
        rounds++;
        streak = correct ? streak + 1 : 0;
        if (streak >= ROUNDS_TO_MASTER) {
            streak = ROUNDS_TO_MASTER;
            state = WordState.MASTERED;
        }
        return new SetStep(streak, state, true);
    }

    /** The word's card turned out to be another card's form (the AI's lemma had a card already). */
    public void pointTo(WordId card) {
        word = Objects.requireNonNull(card, "card");
    }

    public boolean belongsTo(LearnerId caller) {
        return learner.equals(caller);
    }

    public Optional<Instant> learnedAt() {
        return Optional.ofNullable(learnedAt);
    }
}
