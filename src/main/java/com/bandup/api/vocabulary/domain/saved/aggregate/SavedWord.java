package com.bandup.api.vocabulary.domain.saved.aggregate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.shared.domain.ddd.AggregateRoot;
import com.bandup.api.vocabulary.domain.saved.valueobject.Box;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.ReviewSchedule;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSource;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordState;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * A word one learner wants to learn (aggregate root; vocabulary requirements
 * 4–6): a link to the shared card plus the learner's state. NEW until a learn
 * session teaches it, then LEARNING in review boxes 1–4, and MASTERED after a
 * right first answer of the day in box 4. Only the first answer of a day moves
 * the word; a session repeats a wrong word without moving it again.
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class SavedWord {

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
    private Box box;
    private LocalDate dueOn;
    private LocalDate lastAnsweredOn;

    private SavedWord(SavedWordId id, LearnerId learner, WordId word, WordSource source, LocalDate addedOn,
            WordState state, Box box, LocalDate dueOn, LocalDate lastAnsweredOn) {
        this.id = Objects.requireNonNull(id, "id");
        this.learner = Objects.requireNonNull(learner, "learner");
        this.word = Objects.requireNonNull(word, "word");
        this.source = Objects.requireNonNull(source, "source");
        this.addedOn = Objects.requireNonNull(addedOn, "addedOn");
        this.state = Objects.requireNonNull(state, "state");
        this.box = box;
        this.dueOn = dueOn;
        this.lastAnsweredOn = lastAnsweredOn;
    }

    /** A newly saved word (vocabulary requirements 1, 2). */
    public static SavedWord save(LearnerId learner, WordId word, WordSource source, LocalDate today) {
        return new SavedWord(SavedWordId.random(), learner, word, source, today, WordState.NEW, null, null, null);
    }

    public static SavedWord restore(SavedWordId id, LearnerId learner, WordId word, WordSource source,
            LocalDate addedOn, WordState state, Box box, LocalDate dueOn, LocalDate lastAnsweredOn) {
        return new SavedWord(id, learner, word, source, addedOn, state, box, dueOn, lastAnsweredOn);
    }

    /** A learn session taught it (requirement 5.7): box 1, due tomorrow. Already learned words stay as they are. */
    public void learned(LocalDate today) {
        if (state != WordState.NEW) {
            return;
        }
        state = WordState.LEARNING;
        box = Box.first();
        dueOn = box.dueAfter(today);
        lastAnsweredOn = today;
    }

    /** A review answer (requirements 6.2, 6.3). */
    public ReviewSchedule answer(boolean correct, LocalDate today) {
        if (state != WordState.LEARNING) {
            throw new IllegalStateException("Only a word being learned is reviewed, not " + state);
        }
        Box from = box;
        if (today.equals(lastAnsweredOn)) {
            return new ReviewSchedule(from, box, dueOn, state, false);
        }
        lastAnsweredOn = today;
        if (correct && box.isLast()) {
            state = WordState.MASTERED;
            dueOn = null;
            return new ReviewSchedule(from, box, null, state, true);
        }
        box = correct ? box.up() : Box.first();
        dueOn = box.dueAfter(today);
        return new ReviewSchedule(from, box, dueOn, state, true);
    }

    /** The word's card turned out to be another card's form (the AI's lemma had a card already). */
    public void pointTo(WordId card) {
        word = Objects.requireNonNull(card, "card");
    }

    public boolean isDue(LocalDate today) {
        return state == WordState.LEARNING && dueOn != null && !dueOn.isAfter(today);
    }

    public boolean belongsTo(LearnerId caller) {
        return learner.equals(caller);
    }

    public Optional<Box> box() {
        return Optional.ofNullable(box);
    }

    public Optional<LocalDate> dueOn() {
        return Optional.ofNullable(dueOn);
    }

    public Optional<LocalDate> lastAnsweredOn() {
        return Optional.ofNullable(lastAnsweredOn);
    }
}
