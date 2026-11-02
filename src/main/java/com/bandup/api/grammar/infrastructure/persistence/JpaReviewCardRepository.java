package com.bandup.api.grammar.infrastructure.persistence;

import java.time.Clock;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.review.aggregate.ReviewCard;
import com.bandup.api.grammar.domain.review.repository.ReviewCardRepository;
import com.bandup.api.grammar.domain.review.valueobject.Box;
import com.bandup.api.grammar.domain.review.valueobject.ReviewCardId;

/** {@link ReviewCardRepository} on JPA. */
@Repository
@RequiredArgsConstructor
class JpaReviewCardRepository implements ReviewCardRepository {

    private final EntityManager em;
    private final Clock clock;
    private final Learners learners;

    @Override
    @Transactional
    public Optional<ReviewCard> findById(ReviewCardId id) {
        ReviewCardEntity row = em.find(ReviewCardEntity.class, id.value(), LockModeType.PESSIMISTIC_WRITE);
        if (row == null) {
            return Optional.empty();
        }
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(ReviewCard.restore(new ReviewCardId(row.id), learners.learnerId(row.userId),
                new LessonId(row.lessonId), new ItemId(row.itemId), new Box(row.box), row.dueOn, row.lastAnsweredOn));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<ItemId> itemsInDeck(LearnerId learner, LessonId lesson) {
        return em.createQuery("""
                select c.itemId from ReviewCardEntity c where c.userId = :user and c.lessonId = :lesson""", String.class)
                .setParameter("user", learners.userId(learner)).setParameter("lesson", lesson.value())
                .getResultStream().map(ItemId::new).collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void save(ReviewCard card) {
        saveAll(Set.of(card));
    }

    @Override
    @Transactional
    public void saveAll(Collection<ReviewCard> cards) {
        for (ReviewCard card : cards) {
            ReviewCardEntity row = em.find(ReviewCardEntity.class, card.id().value());
            boolean isNew = row == null;
            if (isNew) {
                row = new ReviewCardEntity();
                row.id = card.id().value();
                row.userId = learners.userId(card.learner());
                row.lessonId = card.lesson().value();
                row.itemId = card.item().value();
                row.createdAt = clock.instant();
            }
            row.box = (short) card.box().value();
            row.dueOn = card.dueOn();
            row.lastAnsweredOn = card.lastAnsweredOn().orElse(null);
            if (isNew) {
                em.persist(row);
            }
        }
        em.flush();
    }

    @Override
    @Transactional
    public void deleteForItems(LessonId lesson, Set<ItemId> items) {
        if (items.isEmpty()) {
            return;
        }
        em.createQuery("delete from ReviewCardEntity c where c.lessonId = :lesson and c.itemId in :items")
                .setParameter("lesson", lesson.value())
                .setParameter("items", items.stream().map(ItemId::value).toList()).executeUpdate();
    }
}
