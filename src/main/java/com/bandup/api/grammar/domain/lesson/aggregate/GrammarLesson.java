package com.bandup.api.grammar.domain.lesson.aggregate;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.grammar.domain.lesson.service.AnswerNormaliser;
import com.bandup.api.grammar.domain.lesson.service.LessonPolicy;
import com.bandup.api.grammar.domain.lesson.valueobject.GivenAnswer;
import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemFeedback;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonSlug;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonStatus;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;
import com.bandup.api.grammar.domain.lesson.valueobject.Revision;
import com.bandup.api.shared.domain.ddd.AggregateRoot;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageChain;

/**
 * One grammar lesson (aggregate root; grammar requirements 4, 5 and 9). Every
 * change goes through it:
 *
 * <ul>
 *   <li>{@link #draft} creates it (DRAFT, revision 1); {@link #edit} replaces
 *       its body. Both apply the {@link LessonPolicy}: the format rules hold
 *       for drafts too. Editing a PUBLISHED lesson raises its revision;
 *       ARCHIVED lessons can't be edited.</li>
 *   <li>{@link #publish} puts it in its level's course at a position;
 *       {@link #unpublish} and {@link #archive} (final) change the status.</li>
 *   <li>{@link #grade} checks one answer to one item.</li>
 * </ul>
 *
 * Every failed change throws one {@link DomainException} with all
 * violations and leaves the lesson unchanged.
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class GrammarLesson {

    @EqualsAndHashCode.Include
    @Getter
    private final LessonId id;
    @Getter
    private final LessonSlug slug;
    /** The stored version this lesson was loaded with (optimistic locking; 0 for a new one). */
    @Getter
    private final long version;
    @Getter
    private LessonBody body;
    private Position position;
    @Getter
    private LessonStatus status;
    @Getter
    private Revision revision;
    private Instant publishedAt;

    private GrammarLesson(LessonId id, LessonSlug slug, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.version = version;
    }

    /**
     * A new DRAFT at revision 1.
     *
     * @throws DomainException with every format violation
     */
    public static GrammarLesson draft(LessonId id, LessonSlug slug, LessonBody body, LessonPolicy policy) {
        throwIfAny(policy.check(body));
        GrammarLesson lesson = new GrammarLesson(id, slug, 0);
        lesson.body = body;
        lesson.position = body.position();
        lesson.status = LessonStatus.DRAFT;
        lesson.revision = Revision.first();
        return lesson;
    }

    /**
     * Rebuilds a stored lesson (for the persistence adapter).
     *
     * @throws IllegalArgumentException if the stored body breaks the format rules
     */
    public static GrammarLesson restore(LessonId id, LessonSlug slug, long version, LessonBody body, Position position,
            LessonStatus status, Revision revision, Instant publishedAt, LessonPolicy policy) {
        List<Violation> violations = policy.check(body);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Stored lesson " + slug + " is invalid: " + violations);
        }
        GrammarLesson lesson = new GrammarLesson(id, slug, version);
        lesson.body = body;
        lesson.position = position;
        lesson.status = Objects.requireNonNull(status, "status");
        lesson.revision = Objects.requireNonNull(revision, "revision");
        lesson.publishedAt = publishedAt;
        return lesson;
    }

    /**
     * Replaces the body (grammar requirements 9.2, 9.5). A position in the body
     * moves the lesson; without one it keeps its place.
     *
     * @return the check items that are gone, whose review cards must be dropped (requirement 9.7)
     * @throws DomainException with every violation; the lesson is unchanged
     */
    public Set<ItemId> edit(LessonBody newBody, LessonPolicy policy) {
        if (status == LessonStatus.ARCHIVED) {
            throw DomainException.of("status", "An archived lesson can't be edited.");
        }
        throwIfAny(policy.check(newBody));
        Set<ItemId> kept = newBody.check().stream().map(Item::id).collect(Collectors.toSet());
        Set<ItemId> removed = body.check().stream().map(Item::id).filter(item -> !kept.contains(item))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        body = newBody;
        newBody.wantedPosition().ifPresent(p -> position = p);
        if (status == LessonStatus.PUBLISHED) {
            revision = revision.next(); // runs on the old content keep their revision
        }
        return removed;
    }

    /**
     * Puts the lesson in its level's course (requirement 9.3). The caller
     * passes the lesson's own position, or the end of the course.
     */
    public void publish(Position at, Instant now) {
        if (status == LessonStatus.ARCHIVED) {
            throw DomainException.of("status", "An archived lesson can't be published.");
        }
        position = Objects.requireNonNull(at, "position");
        if (status != LessonStatus.PUBLISHED) {
            status = LessonStatus.PUBLISHED;
            if (publishedAt == null) {
                publishedAt = now;
            }
        }
    }

    public void unpublish() {
        if (status == LessonStatus.ARCHIVED) {
            throw DomainException.of("status", "An archived lesson can't be unpublished.");
        }
        status = LessonStatus.DRAFT;
    }

    /** Takes the lesson out of the course for good; learners' history stays (requirement 9.6). */
    public void archive() {
        status = LessonStatus.ARCHIVED;
    }

    /**
     * Grades one answer to one item (requirement 5.5).
     *
     * @throws DomainException {@code itemId} for an unknown item, {@code answer} when the answer doesn't fit
     */
    public ItemFeedback grade(ItemId item, GivenAnswer answer, AnswerNormaliser normaliser, LanguageChain language) {
        return find(item).orElseThrow(() -> DomainException.of("itemId", "This lesson has no item " + item + "."))
                .grade(answer, normaliser, language);
    }

    public Optional<Item> find(ItemId item) {
        return Stream.concat(body.practice().stream(), body.check().stream())
                .filter(candidate -> candidate.id().equals(item)).findFirst();
    }

    public boolean isCheckItem(ItemId item) {
        return body.check().stream().anyMatch(candidate -> candidate.id().equals(item));
    }

    public List<ItemId> checkItems() {
        return body.check().stream().map(Item::id).toList();
    }

    public GrammarLevel level() {
        return body.level();
    }

    public Optional<Position> position() {
        return Optional.ofNullable(position);
    }

    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public boolean isPublished() {
        return status == LessonStatus.PUBLISHED;
    }

    private static void throwIfAny(List<Violation> violations) {
        if (!violations.isEmpty()) {
            throw new DomainException(violations);
        }
    }
}
