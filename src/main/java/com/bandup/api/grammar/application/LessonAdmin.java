package com.bandup.api.grammar.application;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.grammar.application.port.LessonSummaries;
import com.bandup.api.grammar.application.port.LessonUsage;
import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.repository.GrammarLessonRepository;
import com.bandup.api.grammar.domain.lesson.service.AnswerNormaliser;
import com.bandup.api.grammar.domain.lesson.service.LessonPolicy;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonSlug;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;
import com.bandup.api.grammar.domain.review.repository.ReviewCardRepository;
import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;

/**
 * The admin lesson use cases (grammar requirement 9, design §4.2): list, get,
 * create, update, publish, unpublish, delete. Each runs in one transaction;
 * every write goes through the {@link GrammarLesson} aggregate and the
 * repository, which rejects stale versions (409).
 */
@Service
@RequiredArgsConstructor
public class LessonAdmin {

    private static final LessonPolicy POLICY = new LessonPolicy(new AnswerNormaliser());

    private final GrammarLessonRepository lessons;
    private final ReviewCardRepository cards;
    private final LessonInputBuilder builder;
    private final LessonSummaries summaries;
    private final LessonUsage usage;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LessonSummary> list(String level, String status) {
        return summaries.list(level, status);
    }

    @Transactional(readOnly = true)
    public AdminLessonView get(String slug) {
        GrammarLesson lesson = load(slug);
        return LessonViews.admin(lesson, lesson.version());
    }

    /** A new DRAFT. Every format problem is reported at once (422). */
    @Transactional
    public AdminLessonView create(LessonInput in) {
        LessonSlug slug = new LessonSlug(in.id());
        if (lessons.existsBySlug(slug)) {
            throw new ConflictException("A lesson with the id \"" + slug + "\" already exists.");
        }
        GrammarLesson lesson = GrammarLesson.draft(lessons.nextLessonId(), slug, builder.build(in), POLICY);
        return LessonViews.admin(lesson, lessons.save(lesson));
    }

    /**
     * Replaces the body. A published lesson gets a new revision; review cards
     * of removed check items are dropped (requirement 9.7).
     */
    @Transactional
    public AdminLessonView update(String slug, LessonInput in) {
        GrammarLesson lesson = load(slug);
        if (in.version() == null) {
            throw new InvalidRequestException("The version is required.",
                    List.of(new Violation("version", "Send the version you loaded.")));
        }
        if (in.version() != lesson.version()) {
            throw new ConflictException("This lesson was changed by someone else. Reload it and try again.");
        }
        Set<ItemId> removed = lesson.edit(builder.build(in), POLICY);
        cards.deleteForItems(lesson.id(), removed);
        checkPositionFree(lesson);
        return LessonViews.admin(lesson, lessons.save(lesson));
    }

    /**
     * Puts the lesson in its level's course: at {@code position}, else at its
     * own position, else at the end of the course (requirement 9.3).
     */
    @Transactional
    public AdminLessonView publish(String slug, Integer position) {
        return change(slug, lesson -> {
            Position at = position != null ? new Position(position)
                    : lesson.position().orElseGet(() -> lessons.lastPublishedPosition(lesson.level())
                            .map(Position::next).orElse(new Position(1)));
            lesson.publish(at, clock.instant());
            checkPositionFree(lesson);
        });
    }

    /** Two published lessons of a level can't share a position (requirement 9.4): a clear 409. */
    private void checkPositionFree(GrammarLesson lesson) {
        if (!lesson.isPublished()) {
            return;
        }
        lesson.position().filter(at -> lessons.positionTaken(lesson.level(), at, lesson.id())).ifPresent(at -> {
            throw new ConflictException("Position " + at.value() + " is already used in the " + lesson.level().name()
                    + " course.");
        });
    }

    @Transactional
    public AdminLessonView unpublish(String slug) {
        return change(slug, GrammarLesson::unpublish);
    }

    /**
     * Removes the lesson, or archives it when learners have used it (requirement 9.6).
     *
     * @return true if it was archived instead of removed
     */
    @Transactional
    public boolean delete(String slug) {
        GrammarLesson lesson = load(slug);
        if (usage.isUsed(lesson.id())) {
            lesson.archive();
            lessons.save(lesson);
            return true;
        }
        lessons.delete(lesson);
        return false;
    }

    private AdminLessonView change(String slug, Consumer<GrammarLesson> change) {
        GrammarLesson lesson = load(slug);
        change.accept(lesson);
        return LessonViews.admin(lesson, lessons.save(lesson));
    }

    private GrammarLesson load(String slug) {
        LessonSlug parsed;
        try {
            parsed = new LessonSlug(slug);
        } catch (DomainException e) {
            throw notFound();
        }
        return lessons.findBySlug(parsed).orElseThrow(LessonAdmin::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("No such lesson.");
    }
}
