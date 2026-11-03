package com.bandup.api.vocabulary.domain.saved.repository;

import java.util.List;
import java.util.Optional;

import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/** Loads and stores learners' saved words. Implemented in infrastructure. */
public interface SavedWordRepository {

    Optional<SavedWord> findById(SavedWordId id);

    Optional<SavedWord> find(LearnerId learner, WordId word);

    /** Every learner's saved word pointing at this card. */
    List<SavedWord> findByWord(WordId word);

    void save(SavedWord word);

    void delete(SavedWord word);
}
