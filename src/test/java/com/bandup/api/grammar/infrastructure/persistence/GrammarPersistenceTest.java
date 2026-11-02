package com.bandup.api.grammar.infrastructure.persistence;

import static com.bandup.api.grammar.domain.lesson.LessonFixtures.body;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.en;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.enAz;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.withPosition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.repository.GrammarLessonRepository;
import com.bandup.api.grammar.domain.lesson.service.AnswerNormaliser;
import com.bandup.api.grammar.domain.lesson.service.LessonPolicy;
import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LearnCard;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonSlug;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonStatus;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;
import com.bandup.api.grammar.domain.lesson.valueobject.Revision;
import com.bandup.api.grammar.domain.lesson.valueobject.Topic;
import com.bandup.api.grammar.domain.progress.aggregate.GrammarProgress;
import com.bandup.api.grammar.domain.progress.repository.GrammarProgressRepository;
import com.bandup.api.grammar.domain.progress.valueobject.CourseLevel;
import com.bandup.api.grammar.domain.progress.valueobject.FinishedLesson;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.review.aggregate.ReviewCard;
import com.bandup.api.grammar.domain.review.repository.ReviewCardRepository;
import com.bandup.api.grammar.domain.review.valueobject.Box;
import com.bandup.api.grammar.domain.run.aggregate.LessonRun;
import com.bandup.api.grammar.domain.run.repository.LessonRunRepository;
import com.bandup.api.grammar.domain.run.valueobject.LessonRevision;
import com.bandup.api.shared.application.ConflictException;

/** The grammar adapters against the real V9 schema (Hibernate validates every entity). Tests are rolled back. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({TestcontainersConfiguration.class, JpaGrammarLessonRepository.class, JpaGrammarProgressRepository.class,
        JpaLessonRunRepository.class, JpaReviewCardRepository.class, Learners.class,
        GrammarPersistenceTest.Clocks.class})
class GrammarPersistenceTest {

    private static final LessonPolicy POLICY = new LessonPolicy(new AnswerNormaliser());
    private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @TestConfiguration(proxyBeanMethods = false)
    static class Clocks {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired GrammarLessonRepository lessons;
    @Autowired GrammarProgressRepository progresses;
    @Autowired LessonRunRepository runs;
    @Autowired ReviewCardRepository cards;
    @Autowired TestEntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private GrammarLesson newLesson(String slug, LessonBody body) {
        GrammarLesson lesson = GrammarLesson.draft(lessons.nextLessonId(), new LessonSlug(slug), body, POLICY);
        lessons.save(lesson);
        return lessons.findBySlug(new LessonSlug(slug)).orElseThrow();
    }

    private LearnerId newLearner() {
        UUID publicId = UUID.randomUUID();
        jdbc.update("insert into app_user (public_id, email, ui_language, support_language) values (?, ?, 'en', 'en')",
                publicId, publicId + "@example.com");
        return new LearnerId(publicId);
    }

    /** A body with every optional part and a second language, to prove nothing is lost. */
    private static LessonBody richBody() {
        LessonBody b = body();
        LearnCard card = new LearnCard("🔒", enAz("Golden rule", "Qızıl qayda"), en("No “would” in the if-clause"),
                enAz("Keep <b>would</b> in the result.", "<b>would</b> nəticədə qalır."), List.of(en("1️⃣ Past?")),
                List.of(new LearnCard.Example(false, en("If I <b>would have</b> known…")),
                        new LearnCard.Example(true, en("If I <b>had</b> known…"))),
                enAz("Never put would in the if-clause.", "Heç vaxt."));
        return new LessonBody(GrammarLevel.C1, new Topic("conditionals", enAz("Conditionals", "Şərt cümlələri")),
                enAz("2nd vs 3rd", "2-ci və 3-cü"), b.goal(), 25, null, List.of(card, b.learn().get(1), b.learn().get(2)),
                b.worked(), b.practice(), b.check());
    }

    // ---------------------------------------------------------------- lessons

    @Test
    void aLessonRoundTripsWithEveryPartAndLanguage() {
        GrammarLesson saved = newLesson("c1-conditionals-1", richBody());
        assertThat(saved.body()).isEqualTo(richBody());
        assertThat(saved.status()).isEqualTo(LessonStatus.DRAFT);
        assertThat(saved.revision()).isEqualTo(Revision.first());
        assertThat(saved.version()).isZero();
        assertThat(lessons.existsBySlug(new LessonSlug("c1-conditionals-1"))).isTrue();
        assertThat(lessons.existsBySlug(new LessonSlug("c1-nothing"))).isFalse();
        assertThat(lessons.findById(saved.id())).contains(saved);
        assertThat(jdbc.queryForMap("select level, topic_key, title from grammar_lesson where slug = 'c1-conditionals-1'"))
                .containsEntry("level", "C1").containsEntry("topic_key", "conditionals")
                .containsEntry("title", "2nd vs 3rd");
    }

    @Test
    void theChooseWhysAndOptionalShowSurviveAndAbsentOnesStayAbsent() {
        GrammarLesson saved = newLesson("b1-items-1", body());
        Item.Choose c1 = (Item.Choose) saved.find(new ItemId("c1")).orElseThrow();
        assertThat(c1.whys()).hasSize(4);
        assertThat(((Item.Type) saved.find(new ItemId("c2")).orElseThrow()).show()).isEqualTo("won");
        assertThat(((Item.Fix) saved.find(new ItemId("c4")).orElseThrow()).show()).isNull();
        assertThat(saved.body().learn().get(2).bodyText()).isEmpty();
    }

    @Test
    void publishingStoresThePositionAndEachSaveRaisesTheVersion() {
        GrammarLesson lesson = newLesson("b1-pp-1", body());
        lesson.publish(new Position(101), NOW);
        assertThat(lessons.save(lesson)).isEqualTo(1);
        GrammarLesson published = lessons.findBySlug(new LessonSlug("b1-pp-1")).orElseThrow();
        assertThat(published.position()).contains(new Position(101));
        assertThat(published.publishedAt()).contains(NOW);
        assertThat(published.isPublished()).isTrue();
        published.edit(body(), POLICY);
        assertThat(lessons.save(published)).isEqualTo(2);
        assertThat(lessons.findBySlug(new LessonSlug("b1-pp-1")).orElseThrow().revision()).isEqualTo(new Revision(2));
    }

    @Test
    void aStaleSaveOrDeleteIsAConflict() {
        GrammarLesson first = newLesson("b1-stale-1", body());
        GrammarLesson second = lessons.findBySlug(new LessonSlug("b1-stale-1")).orElseThrow();
        lessons.save(first);
        assertThatThrownBy(() -> lessons.save(second)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> lessons.delete(second)).isInstanceOf(ConflictException.class);
    }

    @Test
    void theLastPublishedPositionIsPerLevelAndIgnoresDrafts() {
        jdbc.update("update grammar_lesson set status = 'DRAFT'"); // set the seed lessons aside (rolled back)
        assertThat(lessons.lastPublishedPosition(GrammarLevel.B2)).isEmpty();
        GrammarLesson one = newLesson("b1-last-1", body());
        one.publish(new Position(4), NOW);
        lessons.save(one);
        newLesson("b1-last-2", withPosition(body(), 9));
        assertThat(lessons.lastPublishedPosition(GrammarLevel.B1)).contains(new Position(4));
        assertThat(lessons.lastPublishedPosition(GrammarLevel.B2)).isEmpty();
    }

    @Test
    void twoPublishedLessonsOfALevelCantShareAPosition() {
        GrammarLesson one = newLesson("b1-pos-1", body());
        one.publish(new Position(2), NOW);
        lessons.save(one);
        GrammarLesson two = newLesson("b1-pos-2", body());
        two.publish(new Position(2), NOW);
        assertThatThrownBy(() -> lessons.save(two)).hasStackTraceContaining("grammar_lesson_position_uq");
    }

    @Test
    void aDeletedLessonIsGone() {
        GrammarLesson lesson = newLesson("b1-gone-1", body());
        lessons.delete(lesson);
        assertThat(lessons.findBySlug(new LessonSlug("b1-gone-1"))).isEmpty();
    }

    // --------------------------------------------------------------- progress

    @Test
    void progressRoundTripsAndKeepsReplaysAndTheLevel() {
        LearnerId anna = newLearner();
        assertThat(progresses.findByLearner(anna)).isEmpty();
        LessonId one = newLesson("b1-prog-1", body()).id();
        LessonId two = newLesson("b1-prog-2", body()).id();

        GrammarProgress progress = GrammarProgress.start(anna);
        progress.chooseLevel(CourseLevel.B1);
        progress.finish(one, CourseLevel.B1, TODAY);
        progresses.save(progress);
        entityManager.clear();

        GrammarProgress loaded = progresses.findByLearner(anna).orElseThrow();
        assertThat(loaded.level()).contains(CourseLevel.B1);
        loaded.finish(one, CourseLevel.B1, TODAY.plusDays(1));
        loaded.finish(two, CourseLevel.B1, TODAY.plusDays(1));
        loaded.chooseLevel(CourseLevel.C1);
        progresses.save(loaded);
        entityManager.clear();

        GrammarProgress again = progresses.findByLearner(anna).orElseThrow();
        assertThat(again.level()).contains(CourseLevel.C1);
        assertThat(again.finished()).containsOnlyKeys(one, two)
                .containsEntry(one, new FinishedLesson(CourseLevel.B1, TODAY, 2))
                .containsEntry(two, new FinishedLesson(CourseLevel.B1, TODAY.plusDays(1), 1));
    }

    // ------------------------------------------------------------------- runs

    @Test
    void runsOfLearnersAndGuestsRoundTrip() {
        LearnerId anna = newLearner();
        LessonId lesson = newLesson("b1-run-1", body()).id();
        LessonRun run = LessonRun.start(lesson, new LessonRevision(1), Optional.of(anna), NOW);
        run.record(new ItemId("c1"), false, true, 2, NOW);
        run.record(new ItemId("c1"), true, true, 2, NOW);
        runs.save(run);
        LessonRun guest = LessonRun.start(lesson, new LessonRevision(1), Optional.empty(), NOW);
        runs.save(guest);
        entityManager.clear();

        LessonRun loaded = runs.findById(run.id()).orElseThrow();
        assertThat(loaded.learner()).contains(anna);
        assertThat(loaded.mastered()).containsExactly(new ItemId("c1"));
        assertThat(loaded.retries()).isEqualTo(1);
        assertThat(loaded.answered()).isEqualTo(2);
        assertThat(loaded.isFinished()).isFalse();

        loaded.record(new ItemId("c2"), true, true, 2, NOW.plusSeconds(60));
        runs.save(loaded);
        entityManager.clear();
        assertThat(runs.findById(run.id()).orElseThrow().finishedAt()).contains(NOW.plusSeconds(60));
        assertThat(runs.findById(guest.id()).orElseThrow().learner()).isEmpty();
    }

    @Test
    void theCleanupDeletesOnlyOldUnfinishedRuns() {
        LessonId lesson = newLesson("b1-clean-1", body()).id();
        Instant old = NOW.minus(8, ChronoUnit.DAYS);
        LessonRun oldOpen = LessonRun.start(lesson, new LessonRevision(1), Optional.empty(), old);
        LessonRun oldDone = LessonRun.start(lesson, new LessonRevision(1), Optional.empty(), old);
        oldDone.record(new ItemId("c1"), true, true, 1, old);
        LessonRun fresh = LessonRun.start(lesson, new LessonRevision(1), Optional.empty(), NOW);
        List.of(oldOpen, oldDone, fresh).forEach(runs::save);

        assertThat(runs.deleteUnfinishedStartedBefore(NOW.minus(7, ChronoUnit.DAYS))).isEqualTo(1);
        entityManager.clear();
        assertThat(runs.findById(oldOpen.id())).isEmpty();
        assertThat(runs.findById(oldDone.id())).isPresent();
        assertThat(runs.findById(fresh.id())).isPresent();
    }

    // ----------------------------------------------------------- review cards

    @Test
    void reviewCardsRoundTripAndCanBeDroppedPerItem() {
        LearnerId anna = newLearner();
        LessonId lesson = newLesson("b1-cards-1", body()).id();
        ReviewCard c1 = ReviewCard.create(anna, lesson, new ItemId("c1"), TODAY);
        ReviewCard c2 = ReviewCard.create(anna, lesson, new ItemId("c2"), TODAY);
        cards.saveAll(List.of(c1, c2));
        c1.answer(true, TODAY.plusDays(1));
        cards.save(c1);
        entityManager.clear();

        ReviewCard loaded = cards.findById(c1.id()).orElseThrow();
        assertThat(loaded.box()).isEqualTo(new Box(2));
        assertThat(loaded.dueOn()).isEqualTo(TODAY.plusDays(4));
        assertThat(loaded.lastAnsweredOn()).contains(TODAY.plusDays(1));
        assertThat(loaded.learner()).isEqualTo(anna);
        assertThat(cards.itemsInDeck(anna, lesson)).containsExactlyInAnyOrder(new ItemId("c1"), new ItemId("c2"));
        assertThat(cards.itemsInDeck(newLearner(), lesson)).isEmpty();

        cards.deleteForItems(lesson, Set.of(new ItemId("c2")));
        assertThat(cards.itemsInDeck(anna, lesson)).containsExactly(new ItemId("c1"));
    }

    @Test
    void anItemIsInADeckOnlyOnce() {
        LearnerId anna = newLearner();
        LessonId lesson = newLesson("b1-once-1", body()).id();
        cards.save(ReviewCard.create(anna, lesson, new ItemId("c1"), TODAY));
        assertThatThrownBy(() -> cards.save(ReviewCard.create(anna, lesson, new ItemId("c1"), TODAY)))
                .hasStackTraceContaining("grammar_review_card_item_uq");
    }

    @Test
    void aLessonUsedByRunsOrCardsCantBeDeleted() {
        LessonId lesson = newLesson("b1-used-1", body()).id();
        runs.save(LessonRun.start(lesson, new LessonRevision(1), Optional.empty(), NOW));
        GrammarLesson loaded = lessons.findById(lesson).orElseThrow();
        assertThatThrownBy(() -> lessons.delete(loaded)).hasStackTraceContaining("grammar_run_lesson_id_fkey");
    }
}
