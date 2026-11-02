package com.bandup.api.grammar.domain.lesson.aggregate;

import static com.bandup.api.grammar.domain.lesson.LessonFixtures.body;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.en;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.type;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.withCheck;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.withPosition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bandup.api.grammar.domain.lesson.service.AnswerNormaliser;
import com.bandup.api.grammar.domain.lesson.service.LessonPolicy;
import com.bandup.api.grammar.domain.lesson.valueobject.GivenAnswer;
import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonSlug;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonStatus;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;
import com.bandup.api.grammar.domain.lesson.valueobject.Revision;
import com.bandup.api.grammar.domain.lesson.valueobject.Topic;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.language.LanguageChain;

class GrammarLessonTest {

    private static final Instant MONDAY = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-11-03T08:00:00Z");

    private final AnswerNormaliser normaliser = new AnswerNormaliser();
    private final LessonPolicy policy = new LessonPolicy(normaliser);

    private GrammarLesson draft() {
        return GrammarLesson.draft(new LessonId(1), new LessonSlug("b1-present-perfect-1"), body(), policy);
    }

    @Test
    void aDraftStartsAtRevisionOneWithoutAPosition() {
        GrammarLesson lesson = draft();
        assertThat(lesson.status()).isEqualTo(LessonStatus.DRAFT);
        assertThat(lesson.revision()).isEqualTo(Revision.first());
        assertThat(lesson.position()).isEmpty();
        assertThat(lesson.level()).isEqualTo(GrammarLevel.B1);
        assertThat(lesson.version()).isZero();
        assertThat(lesson.checkItems()).extracting(ItemId::value).containsExactly("c1", "c2", "c3", "c4", "c5");
    }

    @Test
    void anInvalidBodyIsRefusedWithEveryViolation() {
        LessonBody b = body();
        LessonBody broken = new LessonBody(b.level(), new Topic("BAD KEY", en("Topic")), en(""), b.goal(), 99, null,
                b.learn(), b.worked(), b.practice(), b.check());
        assertThatThrownBy(() -> GrammarLesson.draft(new LessonId(1), new LessonSlug("x-1"), broken, policy))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.violations())
                        .extracting(v -> v.field()).containsExactlyInAnyOrder("topic.key", "title", "minutes"));
    }

    @Test
    void theSlugFollowsTheFormat() {
        for (String bad : List.of("ab", "B1-lesson", "b1--lesson", "-b1", "b1 lesson", "x".repeat(81))) {
            assertThatThrownBy(() -> new LessonSlug(bad)).isInstanceOf(DomainException.class);
        }
        assertThat(new LessonSlug("c1-inversion-1").value()).isEqualTo("c1-inversion-1");
    }

    @Test
    void publishingSetsThePositionAndTheFirstPublishTime() {
        GrammarLesson lesson = draft();
        lesson.publish(new Position(3), MONDAY);
        assertThat(lesson.isPublished()).isTrue();
        assertThat(lesson.position()).contains(new Position(3));
        assertThat(lesson.publishedAt()).contains(MONDAY);

        lesson.unpublish();
        assertThat(lesson.status()).isEqualTo(LessonStatus.DRAFT);
        lesson.publish(new Position(4), TUESDAY);
        assertThat(lesson.publishedAt()).as("kept from the first publish").contains(MONDAY);
        assertThat(lesson.position()).contains(new Position(4));
    }

    @Test
    void editingADraftKeepsItsRevision() {
        GrammarLesson lesson = draft();
        lesson.edit(body(), policy);
        assertThat(lesson.revision()).isEqualTo(Revision.first());
    }

    @Test
    void editingAPublishedLessonRaisesItsRevisionAndReportsRemovedCheckItems() {
        GrammarLesson lesson = draft();
        lesson.publish(new Position(1), MONDAY);
        LessonBody changed = withCheck(list -> {
            list.remove(2); // c3
            list.add(type("c6", "She ___ it.", List.of("did"), null));
            return list;
        });
        assertThat(lesson.edit(changed, policy)).containsExactly(new ItemId("c3"));
        assertThat(lesson.revision()).isEqualTo(new Revision(2));
        assertThat(lesson.body()).isEqualTo(changed);
        assertThat(lesson.position()).as("no position in the body keeps the place").contains(new Position(1));
    }

    @Test
    void aPositionInTheBodyMovesTheLesson() {
        GrammarLesson lesson = draft();
        lesson.publish(new Position(1), MONDAY);
        lesson.edit(withPosition(body(), 5), policy);
        assertThat(lesson.position()).contains(new Position(5));
    }

    @Test
    void aFailedEditLeavesTheLessonUnchanged() {
        GrammarLesson lesson = draft();
        lesson.publish(new Position(1), MONDAY);
        LessonBody broken = withCheck(list -> list.subList(0, 2));
        assertThatThrownBy(() -> lesson.edit(broken, policy)).isInstanceOf(DomainException.class);
        assertThat(lesson.body()).isEqualTo(body());
        assertThat(lesson.revision()).isEqualTo(Revision.first());
    }

    @Test
    void anArchivedLessonCanNoLongerChange() {
        GrammarLesson lesson = draft();
        lesson.archive();
        assertThat(lesson.status()).isEqualTo(LessonStatus.ARCHIVED);
        assertThatThrownBy(() -> lesson.edit(body(), policy)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> lesson.publish(new Position(1), MONDAY)).isInstanceOf(DomainException.class);
        assertThatThrownBy(lesson::unpublish).isInstanceOf(DomainException.class);
    }

    @Test
    void gradesPracticeAndCheckItemsAndRefusesUnknownOnes() {
        GrammarLesson lesson = draft();
        LanguageChain en = LanguageChain.englishOnly();
        assertThat(lesson.grade(new ItemId("p1"), new GivenAnswer.Choice(1), normaliser, en).correct()).isTrue();
        assertThat(lesson.grade(new ItemId("c2"), new GivenAnswer.Text("Won"), normaliser, en).correct()).isTrue();
        assertThat(lesson.isCheckItem(new ItemId("c2"))).isTrue();
        assertThat(lesson.isCheckItem(new ItemId("p1"))).isFalse();
        assertThatThrownBy(() -> lesson.grade(new ItemId("c99"), new GivenAnswer.Text("x"), normaliser, en))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.violations().get(0).field()).isEqualTo("itemId"));
    }

    @Test
    void restoreRefusesAStoredBodyThatBreaksTheFormat() {
        LessonBody broken = withCheck(list -> list.subList(0, 1));
        assertThatThrownBy(() -> GrammarLesson.restore(new LessonId(1), new LessonSlug("b1-x"), 3, broken, null,
                LessonStatus.DRAFT, Revision.first(), null, policy)).isInstanceOf(IllegalArgumentException.class);
        GrammarLesson restored = GrammarLesson.restore(new LessonId(1), new LessonSlug("b1-x"), 3, body(),
                new Position(2), LessonStatus.PUBLISHED, new Revision(4), MONDAY, policy);
        assertThat(restored.version()).isEqualTo(3);
        assertThat(restored.revision()).isEqualTo(new Revision(4));
        assertThat(restored).isEqualTo(draft()).as("equal by id");
    }
}
