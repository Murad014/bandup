package com.bandup.api.vocabulary.domain.word.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/** Loads and stores word cards whole, with their forms. Implemented in infrastructure. */
public interface WordCardRepository {

    /** The card that owns this form, if any (vocabulary requirement 3.2). */
    Optional<WordCard> findByForm(WordForm form);

    /** The cards owning any of these forms. */
    List<WordCard> findByForms(Collection<WordForm> forms);

    Optional<WordCard> findById(WordId id);

    /** Loads the card and locks it until the transaction ends. */
    Optional<WordCard> lock(WordId id);

    WordId nextId();

    /** PREPARING cards last changed before the cutoff (the app stopped while the AI wrote them). */
    List<WordId> findPreparingUpdatedBefore(Instant cutoff);

    /**
     * Stores a new PREPARING card unless another card owns its form already
     * (two learners saving the same new word at once: only one card, one AI call).
     *
     * @return false when the form was taken; nothing was stored
     */
    boolean addIfFormFree(WordCard card);

    /**
     * Updates the card and its forms. Fails with a conflict if the stored
     * version isn't the one the card was loaded with.
     *
     * @return the new stored version
     */
    long save(WordCard card);

    void delete(WordCard card);
}
