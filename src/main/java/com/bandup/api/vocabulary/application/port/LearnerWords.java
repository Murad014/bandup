package com.bandup.api.vocabulary.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.bandup.api.vocabulary.application.view.VocabularyViews.Counts;
import com.bandup.api.vocabulary.application.view.VocabularyViews.SavedWordView;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;

/** Port: the My words read model (SQL over saved words and cards). */
public interface LearnerWords {

    /**
     * @param state NEW, LEARNING, MASTERED or null for all
     * @param query    part of the word or its meaning; null for all
     * @param exercise only the words saved from this passage (slug); null for all
     */
    List<SavedWordView> page(LearnerId learner, String state, String query, String exercise, int page, int size);

    long total(LearnerId learner, String state, String query, String exercise);

    List<SavedWordView> byIds(LearnerId learner, Collection<UUID> ids);

    Counts counts(LearnerId learner);

    int due(LearnerId learner, LocalDate today);

    /** NEW words whose card is READY (they can be learned now). */
    int newReady(LearnerId learner);

    /** Up to {@code limit} NEW words with READY cards, oldest saved first; only one passage's when given. */
    List<UUID> learnable(LearnerId learner, String exercise, int limit);

    /** Up to {@code limit} LEARNING words due by {@code today}, with READY cards, oldest due first. */
    List<UUID> due(LearnerId learner, LocalDate today, int limit);

    /** Every learned word (LEARNING or MASTERED) with a READY card, for a recall session (requirement 11). */
    List<Recallable> recallable(LearnerId learner);

    /**
     * @param saved     the saved word's id
     * @param meaningAz the card's Azerbaijani meaning
     * @param spellings the card's base form and its other forms
     */
    record Recallable(UUID saved, String meaningAz, List<String> spellings) {

        public Recallable {
            spellings = List.copyOf(spellings);
        }
    }
}
