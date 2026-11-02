package com.bandup.api.grammar.infrastructure.persistence;

import java.time.Clock;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.repository.GrammarLessonRepository;
import com.bandup.api.grammar.domain.lesson.service.AnswerNormaliser;
import com.bandup.api.grammar.domain.lesson.service.LessonPolicy;
import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonSlug;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonStatus;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;
import com.bandup.api.grammar.domain.lesson.valueobject.Revision;
import com.bandup.api.shared.application.ConflictException;

/**
 * {@link GrammarLessonRepository} on JPA. A save locks the lesson row
 * ({@code FOR UPDATE}), checks the version the lesson was loaded with (stale →
 * {@link ConflictException}) and raises it, like the exercise adapter.
 */
@Repository
@RequiredArgsConstructor
class JpaGrammarLessonRepository implements GrammarLessonRepository {

    private static final LessonPolicy POLICY = new LessonPolicy(new AnswerNormaliser());

    private final EntityManager em;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<GrammarLesson> findBySlug(LessonSlug slug) {
        return em.createQuery("select l from GrammarLessonEntity l where l.slug = :slug", GrammarLessonEntity.class)
                .setParameter("slug", slug.value()).getResultStream().findFirst().map(JpaGrammarLessonRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GrammarLesson> findById(LessonId id) {
        return Optional.ofNullable(em.find(GrammarLessonEntity.class, id.value())).map(JpaGrammarLessonRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(LessonSlug slug) {
        return !em.createQuery("select l.id from GrammarLessonEntity l where l.slug = :slug", Long.class)
                .setParameter("slug", slug.value()).getResultList().isEmpty();
    }

    @Override
    @Transactional
    public LessonId nextLessonId() {
        return new LessonId(((Number) em.createNativeQuery(
                "select nextval(pg_get_serial_sequence('grammar_lesson', 'id'))").getSingleResult()).longValue());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean positionTaken(GrammarLevel level, Position position, LessonId except) {
        return !em.createQuery("""
                select l.id from GrammarLessonEntity l
                where l.level = :level and l.status = 'PUBLISHED' and l.position = :position and l.id <> :except""",
                Long.class)
                .setParameter("level", level.name()).setParameter("position", position.value())
                .setParameter("except", except.value()).setMaxResults(1).getResultList().isEmpty();
    }

    @Override
    public Optional<Position> lastPublishedPosition(GrammarLevel level) {
        Integer last = em.createQuery("""
                select max(l.position) from GrammarLessonEntity l
                where l.level = :level and l.status = 'PUBLISHED'""", Integer.class)
                .setParameter("level", level.name()).getSingleResult();
        return Optional.ofNullable(last).map(Position::new);
    }

    @Override
    @Transactional
    public long save(GrammarLesson lesson) {
        GrammarLessonEntity row = lockedRow(lesson.id().value());
        if (row == null) {
            if (lesson.version() != 0) {
                throw stale();
            }
            row = new GrammarLessonEntity();
            row.id = lesson.id().value();
            row.version = 0;
            row.createdAt = clock.instant();
            copy(lesson, row);
            em.persist(row);
        } else {
            if (row.version != lesson.version()) {
                throw stale();
            }
            row.version = row.version + 1;
            copy(lesson, row);
        }
        em.flush(); // database rules (unique slug, one lesson per position) fail here
        return row.version;
    }

    @Override
    @Transactional
    public void delete(GrammarLesson lesson) {
        GrammarLessonEntity row = lockedRow(lesson.id().value());
        if (row == null || row.version != lesson.version()) {
            throw stale();
        }
        em.remove(row);
        em.flush();
    }

    /** The row, locked and re-read, so a save that committed meanwhile is seen. */
    private GrammarLessonEntity lockedRow(long id) {
        GrammarLessonEntity row = em.find(GrammarLessonEntity.class, id);
        if (row != null) {
            em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        }
        return row;
    }

    private static ConflictException stale() {
        return new ConflictException("This lesson was changed by someone else. Reload it and try again.");
    }

    private void copy(GrammarLesson lesson, GrammarLessonEntity row) {
        row.slug = lesson.slug().value();
        row.level = lesson.level().name();
        row.topicKey = lesson.body().topic().key();
        row.title = lesson.body().title().inEnglish();
        row.position = lesson.position().map(Position::value).orElse(null);
        row.status = lesson.status().name();
        row.revision = lesson.revision().value();
        row.content = LessonJson.write(lesson.body());
        row.publishedAt = lesson.publishedAt().orElse(null);
        row.updatedAt = clock.instant();
    }

    private static GrammarLesson toDomain(GrammarLessonEntity row) {
        return GrammarLesson.restore(new LessonId(row.id), new LessonSlug(row.slug), row.version,
                LessonJson.read(row.content), row.position == null ? null : new Position(row.position),
                LessonStatus.valueOf(row.status), new Revision(row.revision), row.publishedAt, POLICY);
    }
}
