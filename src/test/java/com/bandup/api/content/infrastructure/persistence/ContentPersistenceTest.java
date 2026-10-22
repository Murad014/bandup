package com.bandup.api.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.content.domain.exercise.aggregate.Exercise;
import com.bandup.api.content.domain.exercise.entity.Gloss;
import com.bandup.api.content.domain.exercise.entity.Question;
import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.content.domain.exercise.repository.ExerciseRepository;
import com.bandup.api.content.domain.exercise.service.PublishPolicy;
import com.bandup.api.content.domain.exercise.valueobject.AnswerKey;
import com.bandup.api.content.domain.exercise.valueobject.EvidenceKey;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseContent;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseId;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseStatus;
import com.bandup.api.content.domain.exercise.valueobject.Explanation;
import com.bandup.api.content.domain.exercise.valueobject.Format;
import com.bandup.api.content.domain.exercise.valueobject.GlossKey;
import com.bandup.api.content.domain.exercise.valueobject.GlossMeaning;
import com.bandup.api.content.domain.exercise.valueobject.GlossNote;
import com.bandup.api.content.domain.exercise.valueobject.MarkupKeys;
import com.bandup.api.content.domain.exercise.valueobject.Option;
import com.bandup.api.content.domain.exercise.valueobject.OptionKey;
import com.bandup.api.content.domain.exercise.valueobject.QuestionFamily;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.QuestionType;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;
import com.bandup.api.content.domain.exercise.valueobject.Skill;
import com.bandup.api.content.domain.exercise.valueobject.Slug;
import com.bandup.api.content.domain.exercise.valueobject.Stem;
import com.bandup.api.content.domain.exercise.valueobject.TextSource;
import com.bandup.api.content.domain.exercise.valueobject.Timer;
import com.bandup.api.content.domain.exercise.valueobject.TopicId;
import com.bandup.api.content.domain.exercise.valueobject.WordLimit;
import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

/**
 * The Exercise adapter against the real schema (Hibernate validates every
 * entity) and the seeded Wolves exercise. Tests are rolled back, except the
 * concurrency test, which commits and cleans up.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({TestcontainersConfiguration.class, JpaExerciseRepository.class, ContentPersistenceTest.Clocks.class})
class ContentPersistenceTest {

    private static final Slug WOLVES = new Slug("yellowstone-wolves");
    private static final LanguageCode AZ = LanguageCode.of("az");
    private static final PublishPolicy POLICY = new PublishPolicy();

    @TestConfiguration(proxyBeanMethods = false)
    static class Clocks {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired ExerciseRepository exercises;
    @Autowired TestEntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    // ------------------------------------------------------------------ load

    @Test
    void loadsTheSeededWolvesExercise() {
        Exercise wolves = exercises.findBySlug(WOLVES).orElseThrow();

        assertThat(wolves.skill()).isEqualTo(Skill.READING);
        assertThat(wolves.targetLanguage()).isEqualTo(LanguageCode.ENGLISH);
        assertThat(wolves.title()).isEqualTo("The Return of the Wolves");
        assertThat(wolves.format()).isEqualTo(Format.MINI);
        assertThat(wolves.level()).hasValueSatisfying(l -> assertThat(l.name()).isEqualTo("B1"));
        assertThat(wolves.timer()).contains(new Timer(300, 60));
        assertThat(wolves.placement().orElseThrow().position()).isEqualTo(4);
        assertThat(wolves.topics()).hasSize(1);
        assertThat(wolves.status()).isEqualTo(ExerciseStatus.PUBLISHED);
        assertThat(wolves.revision().value()).isEqualTo(1);
        assertThat(wolves.version()).isZero();
        assertThat(wolves.publishedAt()).isPresent();

        var passage = wolves.passage().orElseThrow();
        assertThat(passage.glosses()).hasSize(25);
        assertThat(passage.glosses()).extracting(g -> g.key().value()).startsWith("g1", "g2", "g3").endsWith("g25");
        Gloss g1 = passage.glosses().getFirst();
        assertThat(g1.surface()).isEqualTo("eliminated");
        assertThat(g1.meanings().get(AZ).orElseThrow().text()).isEqualTo("məhv edilmək");
        assertThat(g1.meanings().get(LanguageCode.ENGLISH).orElseThrow().source()).isEqualTo(TextSource.MACHINE);
        assertThat(passage.evidence()).extracting(EvidenceKey::value).contains("e1", "e2", "e4", "e5", "e6");

        assertThat(wolves.groups()).extracting(g -> g.type().code()).containsExactly("TFNG", "FILL");
        QuestionGroup fill = wolves.groups().get(1);
        assertThat(fill.type().family()).isEqualTo(QuestionFamily.GAP);
        assertThat(fill.wordLimit()).contains(new WordLimit(1, false));
        Question q4 = fill.questions().getFirst();
        assertThat(q4.stem()).isEqualTo(new Stem.Gap("Without wolves, too many elk damaged young trees near", "."));
        assertThat(q4.answer()).isEqualTo(AnswerKey.of("riverbanks"));
        assertThat(q4.evidence()).contains(new EvidenceKey("e4"));
        assertThat(q4.explanations().get(LanguageCode.ENGLISH).orElseThrow().html().html()).contains("riverbanks");
        assertThat(POLICY.check(wolves)).isEmpty();
        assertThat(exercises.existsBySlug(WOLVES)).isTrue();
        assertThat(exercises.existsBySlug(new Slug("nothing-here"))).isFalse();
        assertThat(exercises.findBySlug(new Slug("nothing-here"))).isEmpty();
    }

    @Test
    void wolvesSavedBackIsUnchangedAndKeepsItsRowIds() {
        Exercise wolves = exercises.findBySlug(WOLVES).orElseThrow();
        String before = describe(wolves);
        Map<String, Object> idsBefore = rowIds();

        long version = exercises.save(wolves);
        reload();
        Exercise again = exercises.findBySlug(WOLVES).orElseThrow();

        assertThat(version).isEqualTo(1);
        assertThat(again.version()).isEqualTo(1);
        assertThat(describe(again)).isEqualTo(before);
        assertThat(rowIds()).isEqualTo(idsBefore); // questions, groups, glosses and evidence kept their rows
    }

    // ------------------------------------------------------------------ new

    @Test
    void aNewExerciseWithEveryKindOfOptionRoundTrips() {
        Exercise created = Exercise.draft(exercises.nextExerciseId(), new Slug("every-kind"), Skill.READING,
                LanguageCode.ENGLISH, everyKind(List.of()));

        assertThat(exercises.save(created)).isZero();
        reload();
        Exercise loaded = exercises.findBySlug(new Slug("every-kind")).orElseThrow();

        assertThat(describe(loaded)).isEqualTo(describe(created));
        assertThat(loaded.groups().get(2).bank()).hasSize(3);                         // owned by the group
        assertThat(loaded.groups().get(1).questions().getFirst().options()).hasSize(4); // owned by the question
        assertThat(jdbc.queryForObject("select word_count from reading_passage where exercise_id = ?", Integer.class,
                created.id().value())).isEqualTo(created.passage().orElseThrow().wordCount());
    }

    @Test
    void editsKeepIdsWhileGroupsMoveQuestionsMoveAndKeysComeAndGo() {
        Exercise created = Exercise.draft(exercises.nextExerciseId(), new Slug("every-kind"), Skill.READING,
                LanguageCode.ENGLISH, everyKind(List.of()));
        exercises.save(created);
        reload();
        Exercise exercise = exercises.findBySlug(new Slug("every-kind")).orElseThrow();
        List<QuestionGroup> groups = exercise.groups();

        // New order: matching first; the TFNG group is dropped but its question moves into a new TFNG group;
        // the gloss g2 and evidence e2 disappear from the HTML, e3 is new.
        QuestionGroup tfng = groups.get(0);
        QuestionGroup newTfng = new QuestionGroup(exercises.nextGroupId(), tfng.type(), tfng.instruction(),
                Translations.empty(), null, null, false, null, List.of(),
                List.of(tfng.questions().getFirst(), question(exercises.nextQuestionId().value(), "Brand new.", "e3", "TRUE")));
        String html = "<p><span class=\"ev\" id=\"e3\">New evidence.</span> <span class=\"w\" data-g=\"g1\">Wolves</span> "
                + "<span class=\"ev\" id=\"e1\">came back.</span></p>";
        exercise.editContent(everyKind(List.of(groups.get(2), newTfng, groups.get(1), groups.get(3)),
                html, new MarkupKeys(List.of(new MarkupKeys.GlossMark("g1", "Wolves")), List.of("e3", "e1"))), POLICY);
        exercises.save(exercise);
        reload();
        Exercise loaded = exercises.findBySlug(new Slug("every-kind")).orElseThrow();

        assertThat(describe(loaded)).isEqualTo(describe(exercise));
        assertThat(loaded.version()).isEqualTo(1);
        assertThat(loaded.groups()).extracting(QuestionGroup::id)
                .containsExactly(groups.get(2).id(), newTfng.id(), groups.get(1).id(), groups.get(3).id());
        assertThat(loaded.groups().get(1).questions().getFirst().id()).isEqualTo(tfng.questions().getFirst().id());
        assertThat(jdbc.queryForObject("select count(*) from question_group where id = ?", Integer.class, tfng.id().value())).isZero();
        assertThat(loaded.passage().orElseThrow().evidence()).extracting(EvidenceKey::value).containsExactly("e3", "e1");
        assertThat(loaded.passage().orElseThrow().glosses()).extracting(g -> g.key().value()).containsExactly("g1");
        assertThat(jdbc.queryForList("select gloss_key from passage_gloss where exercise_id = ?", String.class,
                exercise.id().value())).containsExactly("g1");
    }

    @Test
    void anExerciseWithoutPassageOrTimerRoundTripsAndDroppingThePassageRemovesItsRows() {
        Exercise exercise = Exercise.draft(exercises.nextExerciseId(), new Slug("no-passage"), Skill.GRAMMAR,
                LanguageCode.ENGLISH, everyKind(List.of()));
        exercises.save(exercise);
        reload();
        exercise = exercises.findBySlug(new Slug("no-passage")).orElseThrow();

        exercise.editContent(new ExerciseContent("Grammar drill", Format.MINI, null, null, null, Set.of(), null,
                MarkupKeys.none(), Map.of(), List.of(exercise.groups().get(1))), POLICY);
        exercises.save(exercise);
        reload();
        Exercise loaded = exercises.findBySlug(new Slug("no-passage")).orElseThrow();

        assertThat(describe(loaded)).isEqualTo(describe(exercise));
        assertThat(loaded.passage()).isEmpty();
        assertThat(loaded.timer()).isEmpty();
        long id = exercise.id().value();
        for (String table : List.of("reading_passage", "passage_gloss", "passage_evidence", "exercise_topic")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where exercise_id = ?", Integer.class, id))
                    .as(table).isZero();
        }
    }

    // ------------------------------------------------------------ versions

    @Test
    void aStaleSaveIsRejectedAndChangesNothing() {
        Exercise first = exercises.findBySlug(WOLVES).orElseThrow();
        reload();
        Exercise second = exercises.findBySlug(WOLVES).orElseThrow();

        exercises.save(first);
        reload();
        second.editContent(contentOf(second, "Someone else's title"), POLICY);

        assertThatThrownBy(() -> exercises.save(second)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("changed by someone else");
        reload();
        assertThat(exercises.findBySlug(WOLVES).orElseThrow().title()).isEqualTo("The Return of the Wolves");
        assertThatThrownBy(() -> exercises.delete(second)).isInstanceOf(ConflictException.class);
    }

    @Test
    void savingAMissingExerciseWithAVersionIsAConflict() {
        Exercise wolves = exercises.findBySlug(WOLVES).orElseThrow();
        Exercise ghost = Exercise.restore(new ExerciseId(999_999), new Slug("ghost"), Skill.READING, LanguageCode.ENGLISH,
                "Ghost", Format.MINI, null, null, null, Set.of(), ExerciseStatus.DRAFT, wolves.revision(), 3, null, null, List.of());

        assertThatThrownBy(() -> exercises.save(ghost)).isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteRemovesEveryRow() {
        Exercise wolves = exercises.findBySlug(WOLVES).orElseThrow();
        long id = wolves.id().value();

        exercises.delete(wolves);

        assertThat(exercises.findBySlug(WOLVES)).isEmpty();
        for (String table : List.of("reading_passage", "passage_gloss", "passage_evidence", "question_group", "question",
                "exercise_topic")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where exercise_id = ?", Integer.class, id))
                    .as(table).isZero();
        }
    }

    @Test
    void newIdsComeFromTheTablesOwnSequences() {
        long a = exercises.nextQuestionId().value();
        long b = exercises.nextQuestionId().value();
        assertThat(b).isGreaterThan(a);
        assertThat(exercises.nextGroupId().value()).isPositive();
        assertThat(exercises.nextExerciseId().value()).isGreaterThan(
                jdbc.queryForObject("select max(id) from exercise", Long.class));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoConcurrentSavesOfOneVersionEndWithExactlyOneConflict() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        long id = tx.execute(s -> {
            Exercise created = Exercise.draft(exercises.nextExerciseId(), new Slug("race"), Skill.READING,
                    LanguageCode.ENGLISH, everyKind(List.of()));
            exercises.save(created);
            return created.id().value();
        });
        try {
            CountDownLatch bothLoaded = new CountDownLatch(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<String>> results = new ArrayList<>();
            for (String title : List.of("Title A", "Title B")) {
                results.add(pool.submit(() -> {
                    try {
                        return tx.execute(s -> {
                            Exercise exercise = exercises.findBySlug(new Slug("race")).orElseThrow();
                            bothLoaded.countDown();
                            await(bothLoaded);
                            exercise.editContent(contentOf(exercise, title), POLICY);
                            exercises.save(exercise);
                            return "saved";
                        });
                    } catch (ConflictException e) {
                        return "conflict";
                    }
                }));
            }
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();

            assertThat(outcomes).containsExactlyInAnyOrder("saved", "conflict");
            assertThat(jdbc.queryForObject("select version from exercise where id = ?", Long.class, id)).isEqualTo(1);
        } finally {
            jdbc.update("delete from exercise where id = ?", id);
        }
    }

    // ------------------------------------------------------------- helpers

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Object> rowIds() {
        Map<String, Object> ids = new TreeMap<>();
        String exercise = "(select id from exercise where slug = 'yellowstone-wolves')";
        ids.put("questions", jdbc.queryForList("select id from question where exercise_id = " + exercise + " order by id", Long.class));
        ids.put("groups", jdbc.queryForList("select id from question_group where exercise_id = " + exercise + " order by id", Long.class));
        ids.put("glosses", jdbc.queryForList("select id || gloss_key from passage_gloss where exercise_id = " + exercise + " order by id", String.class));
        ids.put("evidence", jdbc.queryForList("select id || evidence_key from passage_evidence where exercise_id = " + exercise + " order by id", String.class));
        ids.put("question evidence", jdbc.queryForList("select id || '-' || coalesce(evidence_id, 0) from question where exercise_id = " + exercise + " order by id", String.class));
        return ids;
    }

    private static ExerciseContent contentOf(Exercise e, String title) {
        Map<GlossKey, GlossNote> notes = new LinkedHashMap<>();
        List<MarkupKeys.GlossMark> marks = new ArrayList<>();
        e.passage().ifPresent(p -> p.glosses().forEach(g -> {
            notes.put(g.key(), new GlossNote(g.lemma().orElse(null), g.meanings()));
            marks.add(new MarkupKeys.GlossMark(g.key().value(), g.surface()));
        }));
        MarkupKeys markup = e.passage().map(p -> new MarkupKeys(marks, p.evidence().stream().map(EvidenceKey::value).toList()))
                .orElse(MarkupKeys.none());
        return new ExerciseContent(title, e.format(), e.level().orElse(null), e.timer().orElse(null),
                e.placement().orElse(null), e.topics(), e.passage().map(p -> p.html()).orElse(null), markup, notes, e.groups());
    }

    /** TFNG (no options), MCMA (own options), matching (bank), gap — plus translations everywhere. */
    private ExerciseContent everyKind(List<QuestionGroup> groupsOrEmpty) {
        String html = "<p><span class=\"ev\" id=\"e1\">The <span class=\"w\" data-g=\"g1\">wolves</span> came back</span> "
                + "and <span class=\"ev\" id=\"e2\">the <span class=\"w\" data-g=\"g2\">elk</span> left.</span></p>";
        MarkupKeys markup = new MarkupKeys(List.of(new MarkupKeys.GlossMark("g1", "wolves"), new MarkupKeys.GlossMark("g2", "elk")),
                List.of("e1", "e2"));
        if (!groupsOrEmpty.isEmpty()) {
            return everyKind(groupsOrEmpty, html, markup);
        }
        QuestionGroup tfng = new QuestionGroup(exercises.nextGroupId(), new QuestionType("TFNG", QuestionFamily.CHOICE_FIXED),
                new SanitizedHtml("Do the statements agree?"),
                Translations.<SanitizedHtml>empty().with(AZ, new SanitizedHtml("Fikirlər mətnə uyğundurmu?")),
                null, null, false, null, List.of(), List.of(question(exercises.nextQuestionId().value(), "Wolves came back.", "e1", "TRUE")));
        QuestionGroup chooseTwo = new QuestionGroup(exercises.nextGroupId(), new QuestionType("MCMA", QuestionFamily.MC_MULTI),
                new SanitizedHtml("Choose TWO letters."), Translations.empty(), 2, null, false, null, List.of(),
                List.of(new Question(new QuestionId(exercises.nextQuestionId().value()), new Stem.Text("Which TWO?"), null,
                        List.of(option("A", "Fewer elk"), option("B", "More tourists"), option("C", "Colder winters"),
                                option("D", "More beavers")),
                        AnswerKey.of("D", "A"), explanation())));
        QuestionGroup headings = new QuestionGroup(exercises.nextGroupId(), new QuestionType("MATCH_HEADINGS", QuestionFamily.MATCH),
                new SanitizedHtml("Choose the heading."), Translations.empty(), null, null, false, null,
                List.of(option("i", "Return"), option("ii", "Change"), option("iii", "Doubt")),
                List.of(new Question(new QuestionId(exercises.nextQuestionId().value()), new Stem.Text("Paragraph A"),
                        new EvidenceKey("e1"), List.of(), AnswerKey.of("ii"), explanation())));
        QuestionGroup gap = new QuestionGroup(exercises.nextGroupId(), new QuestionType("FILL", QuestionFamily.GAP),
                new SanitizedHtml("ONE WORD AND/OR A NUMBER."), Translations.empty(), null, new WordLimit(1, true), false, null,
                List.of(), List.of(new Question(new QuestionId(exercises.nextQuestionId().value()), new Stem.Gap("The", "left."),
                        new EvidenceKey("e1"), List.of(), new AnswerKey(List.of("elk", "moose")), explanation())));
        return everyKind(List.of(tfng, chooseTwo, headings, gap), html, markup);
    }

    private static ExerciseContent everyKind(List<QuestionGroup> groups, String html, MarkupKeys markup) {
        Map<GlossKey, GlossNote> notes = Map.of(
                new GlossKey("g1"), new GlossNote("wolf", Translations.<GlossMeaning>empty()
                        .with(AZ, new GlossMeaning("canavarlar", TextSource.HUMAN, true))
                        .with(LanguageCode.ENGLISH, new GlossMeaning("wild dogs", TextSource.MACHINE, false))),
                new GlossKey("g2"), new GlossNote(null, Translations.empty()));
        return new ExerciseContent("Every kind", Format.FULL, null, new Timer(600, null), null,
                Set.of(new TopicId(1), new TopicId(2)), new SanitizedHtml(html), markup, notes, groups);
    }

    private static Question question(long id, String text, String evidence, String answer) {
        return new Question(new QuestionId(id), new Stem.Text(text), evidence == null ? null : new EvidenceKey(evidence),
                List.of(), AnswerKey.of(answer), explanation());
    }

    private static Translations<Explanation> explanation() {
        return Translations.<Explanation>empty()
                .with(LanguageCode.ENGLISH, new Explanation(new SanitizedHtml("<b>Because</b> the text says so."), TextSource.HUMAN, true))
                .with(AZ, new Explanation(new SanitizedHtml("Çünki mətndə belədir."), TextSource.MACHINE, false));
    }

    private static Option option(String key, String label) {
        return new Option(new OptionKey(key), label);
    }

    /** Every field of the aggregate in a canonical text, to compare what was saved with what is loaded. */
    private static String describe(Exercise e) {
        StringBuilder out = new StringBuilder();
        out.append(e.id()).append(e.slug()).append(e.skill()).append(e.targetLanguage()).append(e.title()).append(e.format())
                .append(e.level()).append(e.timer()).append(e.placement()).append(new TreeMap<>(Map.of("t", e.topics().stream()
                        .map(t -> t.value()).sorted().toList()))).append(e.status()).append(e.revision()).append('\n');
        e.passage().ifPresent(p -> {
            out.append(p.html().html()).append(p.evidence()).append('\n');
            p.glosses().forEach(g -> out.append(g.key()).append(g.surface()).append(g.lemma())
                    .append(new TreeMap<>(g.meanings().asMap().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            x -> x.getKey().value(), x -> x.getValue().toString())))).append('\n'));
        });
        for (QuestionGroup g : e.groups()) {
            out.append(g.id()).append(g.type()).append(g.instruction()).append(sorted(g.instructionTranslations()))
                    .append(g.chooseCount()).append(g.wordLimit()).append(g.optionsReusable()).append(g.media())
                    .append(g.bank()).append('\n');
            for (Question q : g.questions()) {
                out.append("  ").append(q.id()).append(q.stem()).append(q.evidence()).append(q.options()).append(q.answer())
                        .append(sorted(q.explanations())).append('\n');
            }
        }
        return out.toString();
    }

    private static <T> Map<String, String> sorted(Translations<T> translations) {
        Map<String, String> map = new TreeMap<>();
        translations.asMap().forEach((k, v) -> map.put(k.value(), String.valueOf(v)));
        return map;
    }
}
