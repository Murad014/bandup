package com.bandup.api.grammar.domain.review.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.review.aggregate.ReviewCard;
import com.bandup.api.grammar.domain.review.valueobject.ReviewCardId;

/** Loads and stores review cards. Implemented in infrastructure. */
public interface ReviewCardRepository {

    /** The card, locked for the rest of the transaction. */
    Optional<ReviewCard> findById(ReviewCardId id);

    /** The items of {@code lesson} already in the learner's deck (so a replay adds no duplicates). */
    Set<ItemId> itemsInDeck(LearnerId learner, LessonId lesson);

    void save(ReviewCard card);

    void saveAll(Collection<ReviewCard> cards);

    /** Drops the cards of items removed from a lesson (grammar requirement 9.7). */
    void deleteForItems(LessonId lesson, Set<ItemId> items);
}
