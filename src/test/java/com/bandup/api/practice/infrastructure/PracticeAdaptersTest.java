package com.bandup.api.practice.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.practice.application.PassageView;
import com.bandup.api.practice.application.port.GradableExercises;
import com.bandup.api.practice.application.port.LearnerPassages;
import com.bandup.api.practice.domain.attempt.aggregate.Attempt;
import com.bandup.api.practice.domain.attempt.repository.AttemptRepository;
import com.bandup.api.practice.domain.attempt.service.Grader;
import com.bandup.api.practice.domain.attempt.valueobject.AnswerSheet;
import com.bandup.api.practice.domain.attempt.valueobject.GivenAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.GradableExercise;
import com.bandup.api.practice.domain.attempt.valueobject.GradableQuestion;
import com.bandup.api.practice.domain.attempt.valueobject.GradingRule;
import com.bandup.api.practice.domain.attempt.valueobject.LearnerId;
import com.bandup.api.practice.domain.attempt.valueobject.WordLimit;
import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.language.LanguageCode;

/**
 * Practice's adapters against the real schema and the seeded Wolves exercise
 * (Hibernate validates the attempt entities). Each test is rolled back.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(TestcontainersConfiguration.class)
@ComponentScan(basePackages = {"com.bandup.api.practice.infrastructure.readmodel",
        "com.bandup.api.practice.infrastructure.persistence"})
class PracticeAdaptersTest {

    private static final String WOLVES = "yellowstone-wolves";
    private static final LanguageCode AZ = LanguageCode.of("az");
    private static final LanguageChain AZ_EN = LanguageChain.of(List.of(AZ, LanguageCode.ENGLISH));
    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

    @Autowired
    GradableExercises gradableExercises;

    @Autowired
    LearnerPassages learnerPassages;

    @Autowired
    AttemptRepository attempts;

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    // ------------------------------------------------------- gradable exercise

    @Test
    void loadsTheSeededWolvesExerciseForGrading() {
        GradableExercise wolves = gradableExercises.findPublished(WOLVES).orElseThrow();

        assertThat(wolves.id().value()).isEqualTo(wolvesId());
        assertThat(wolves.revision().value()).isEqualTo(1);
        assertThat(wolves.questions()).hasSize(6);
        assertThat(wolves.totalPoints()).isEqualTo(6);

        GradableQuestion q1 = wolves.questionNumbered(1).orElseThrow();
        assertThat(q1.rule()).isEqualTo(GradingRule.choiceFixed("FALSE"));
        assertThat(q1.evidence()).contains("e1");
        assertThat(q1.explanations().get(LanguageCode.ENGLISH).orElseThrow()).contains("<b>hunting</b>");

        GradableQuestion q4 = wolves.questionNumbered(4).orElseThrow();
        assertThat(q4.rule()).isEqualTo(GradingRule.gap(List.of("riverbanks"), new WordLimit(1, false)));
        assertThat(q4.evidence()).contains("e4");
        assertThat(wolves.questions()).extracting(q -> q.rule().accepted().getFirst())
                .containsExactly("FALSE", "TRUE", "NOT GIVEN", "riverbanks", "songbirds", "wolves");
    }

    @Test
    void unknownOrUnpublishedExercisesCantBeGraded() {
        assertThat(gradableExercises.findPublished("no-such-exercise")).isEmpty();

        jdbc.update("update exercise set status = 'DRAFT' where slug = ?", WOLVES);
        assertThat(gradableExercises.findPublished(WOLVES)).isEmpty();
    }

    @Test
    void aChooseTwoQuestionIsGradedWithItsTwoKeys() {
        long question = addChooseTwoGroup();

        GradableExercise wolves = gradableExercises.findPublished(WOLVES).orElseThrow();

        GradableQuestion chooseTwo = wolves.questionNumbered(8).orElseThrow();
        assertThat(chooseTwo.id().value()).isEqualTo(question);
        assertThat(chooseTwo.rule()).isEqualTo(GradingRule.mcMulti(List.of("A", "D"), 2));
        assertThat(wolves.totalPoints()).isEqualTo(8);
    }

    // ---------------------------------------------------------- learner view

    @Test
    void wolvesPassageInAzerbaijani() {
        PassageView view = learnerPassages.find(WOLVES, AZ_EN).orElseThrow();

        assertThat(view.slug()).isEqualTo(WOLVES);
        assertThat(view.language()).isEqualTo("az");
        assertThat(view.direction()).isEqualTo("ltr");
        assertThat(view.title()).isEqualTo("The Return of the Wolves");
        assertThat(view.durationSeconds()).contains(300);
        assertThat(view.lowTimeThresholdSeconds()).contains(60);
        assertThat(view.stageTitle()).contains("Mərhələ 1");
        assertThat(view.position()).contains(4);
        assertThat(view.passageHtml()).contains("data-g=\"g1\"").contains("class=\"ev\"");
        assertThat(view.glosses()).hasSize(25).containsEntry("g25", "aid etmək, hesabına yazmaq");

        assertThat(view.groups()).extracting(PassageView.Group::type).containsExactly("TFNG", "FILL");
        PassageView.Group tfng = view.groups().get(0);
        assertThat(tfng.firstNumber()).isEqualTo(1);
        assertThat(tfng.lastNumber()).isEqualTo(3);
        assertThat(tfng.questions().getFirst().text()).contains("Wolves disappeared from Yellowstone mainly because of disease.");
        PassageView.Group fill = view.groups().get(1);
        assertThat(fill.maxWords()).contains(1);
        assertThat(fill.numbersAllowed()).isFalse();
        assertThat(fill.questions()).extracting(PassageView.Question::n).containsExactly(4, 5, 6);
        assertThat(fill.questions().getFirst().pre()).contains("Without wolves, too many elk damaged young trees near");
        assertThat(fill.questions().getFirst().post()).contains(".");
        assertThat(fill.questions().getFirst().text()).isEmpty();
    }

    @Test
    void eachPieceOfHelpTextFallsBackOnItsOwn() {
        // Turkish has nothing stored: stage title and glosses fall back to English, one by one.
        PassageView turkish = learnerPassages.find(WOLVES, LanguageChain.of(List.of(LanguageCode.of("tr"), LanguageCode.ENGLISH)))
                .orElseThrow();
        assertThat(turkish.language()).isEqualTo("tr");
        assertThat(turkish.stageTitle()).contains("Stage 1");
        assertThat(turkish.glosses()).hasSize(25).containsEntry("g1", "removed completely");

        // g1 loses its Azerbaijani meaning: only g1 falls back to English.
        jdbc.update("""
                delete from passage_gloss_translation where language_code = 'az'
                  and gloss_id = (select id from passage_gloss where gloss_key = 'g1')""");
        PassageView az = learnerPassages.find(WOLVES, AZ_EN).orElseThrow();
        assertThat(az.glosses()).containsEntry("g1", "removed completely").containsEntry("g25", "aid etmək, hesabına yazmaq");
    }

    @Test
    void aGlossWithNoMeaningInTheChainIsLeftOut() {
        jdbc.update("""
                delete from passage_gloss_translation
                where gloss_id = (select id from passage_gloss where gloss_key = 'g1')""");

        assertThat(learnerPassages.find(WOLVES, AZ_EN).orElseThrow().glosses()).hasSize(24).doesNotContainKey("g1");
    }

    @Test
    void chooseTwoTakesTwoNumbersAndMatchingGroupsShowTheirBank() {
        addChooseTwoGroup();
        long headings = jdbc.queryForObject("""
                insert into question_group (exercise_id, position, type_code, instruction_html)
                values (?, 4, 'MATCH_HEADINGS', 'Choose the correct heading.') returning id""", Long.class, wolvesId());
        jdbc.update("insert into question_group_translation (group_id, language_code, instruction_html) values (?, 'az', 'Düzgün başlığı seçin.')", headings);
        jdbc.update("insert into question_option (group_id, option_key, label, position) values (?, 'i', 'Wolves return', 1), (?, 'ii', 'Rivers change', 2)", headings, headings);
        jdbc.update("insert into question (exercise_id, group_id, position, stem_text) values (?, ?, 1, 'Paragraph A')", wolvesId(), headings);

        PassageView view = learnerPassages.find(WOLVES, AZ_EN).orElseThrow();

        PassageView.Group chooseTwo = view.groups().get(2);
        assertThat(chooseTwo.type()).isEqualTo("MCMA");
        assertThat(chooseTwo.chooseCount()).contains(2);
        assertThat(chooseTwo.firstNumber()).isEqualTo(7);
        assertThat(chooseTwo.lastNumber()).isEqualTo(8);
        assertThat(chooseTwo.questions().getFirst().n()).isEqualTo(7);
        assertThat(chooseTwo.questions().getFirst().nTo()).contains(8);
        assertThat(chooseTwo.questions().getFirst().options()).extracting(PassageView.Option::key).containsExactly("A", "B", "C", "D");
        assertThat(chooseTwo.options()).isEmpty();

        PassageView.Group match = view.groups().get(3);
        assertThat(match.firstNumber()).isEqualTo(9);
        assertThat(match.instructionTranslated()).contains("Düzgün başlığı seçin.");
        assertThat(match.options()).extracting(PassageView.Option::label).containsExactly("Wolves return", "Rivers change");
        assertThat(match.questions().getFirst().nTo()).isEmpty();
        assertThat(match.chooseCount()).isEmpty();
    }

    @Test
    void onlyPublishedReadingExercisesAreShown() {
        assertThat(learnerPassages.find("no-such-exercise", AZ_EN)).isEmpty();

        jdbc.update("update exercise set skill = 'GRAMMAR' where slug = ?", WOLVES);
        assertThat(learnerPassages.find(WOLVES, AZ_EN)).isEmpty();

        jdbc.update("update exercise set skill = 'READING', status = 'DRAFT' where slug = ?", WOLVES);
        assertThat(learnerPassages.find(WOLVES, AZ_EN)).isEmpty();
    }

    @Test
    void theLearnerViewNeverReadsAnswersExplanationsOrEvidenceLinks() throws Exception {
        Class<?> adapter = Class.forName("com.bandup.api.practice.infrastructure.readmodel.JdbcLearnerPassages");
        List<String> queries = new ArrayList<>();
        for (Field field : adapter.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                field.setAccessible(true);
                queries.add((String) field.get(null));
            }
        }
        assertThat(queries).hasSizeGreaterThanOrEqualTo(8)
                .noneMatch(sql -> sql.contains("question_answer"))
                .noneMatch(sql -> sql.contains("question_explanation"))
                .noneMatch(sql -> sql.contains("evidence"));

        assertThat(fieldNames(PassageView.class))
                .noneMatch(name -> name.toLowerCase().contains("answer"))
                .noneMatch(name -> name.toLowerCase().contains("explanation"))
                .noneMatch(name -> name.toLowerCase().contains("evidence"));
    }

    // --------------------------------------------------------------- attempts

    @Test
    void anAttemptRoundTripsWithItsJsonSnapshots() {
        UUID publicId = addUser("learner-" + UUID.randomUUID() + "@example.com");
        addChooseTwoGroup();
        GradableExercise wolves = gradableExercises.findPublished(WOLVES).orElseThrow();
        Attempt attempt = Attempt.grade(wolves, AnswerSheet.builder()
                        .answer(1, new GivenAnswer.Text("false"))
                        .answer(4, new GivenAnswer.Text(" Riverbanks "))
                        .answer(7, new GivenAnswer.Keys(List.of("A", "C")))
                        .build(),
                AZ, Optional.of(new LearnerId(publicId)), Duration.ofSeconds(214), NOW, new Grader());

        attempts.save(attempt);
        entityManager.flush();
        entityManager.clear();
        Attempt loaded = attempts.findById(attempt.id()).orElseThrow();

        assertThat(loaded).isEqualTo(attempt);
        assertThat(loaded.learner()).contains(new LearnerId(publicId));
        assertThat(loaded.exercise()).isEqualTo(attempt.exercise());
        assertThat(loaded.revision()).isEqualTo(attempt.revision());
        assertThat(loaded.language()).isEqualTo(AZ);
        assertThat(loaded.elapsed()).isEqualTo(Duration.ofSeconds(214));
        assertThat(loaded.submittedAt()).isEqualTo(NOW);
        assertThat(loaded.answers()).isEqualTo(attempt.answers());
        assertThat(loaded.score()).isEqualTo(3);
        assertThat(loaded.total()).isEqualTo(8);

        // Stored as real JSON: a text answer is a string, keys are an array, the snapshot is an array.
        assertThat(jdbc.queryForList("""
                select jsonb_typeof(given_value) || '/' || jsonb_typeof(expected_value)
                from attempt_answer where attempt_id = ? order by question_number""", String.class, attempt.id().value()))
                .containsExactly("string/array", "string/array", "string/array", "string/array", "string/array",
                        "string/array", "array/array");
        assertThat(jdbc.queryForObject("""
                select given_value::text from attempt_answer where attempt_id = ? and question_number = 4""",
                String.class, attempt.id().value())).isEqualTo("\" Riverbanks \"");
        assertThat(jdbc.queryForObject("select score || '/' || total from attempt where id = ?", String.class,
                attempt.id().value())).isEqualTo("3/8");
        assertThat(jdbc.queryForObject("select count(*) from attempt_answer where attempt_id = ? and is_correct",
                Integer.class, attempt.id().value())).isEqualTo(2);
    }

    @Test
    void anAnonymousAttemptHasNoUser() {
        Attempt attempt = gradeWolves(Optional.empty());

        attempts.save(attempt);
        entityManager.flush();
        entityManager.clear();

        assertThat(attempts.findById(attempt.id()).orElseThrow().learner()).isEmpty();
        assertThat(jdbc.queryForObject("select user_id from attempt where id = ?", Long.class, attempt.id().value())).isNull();
    }

    @Test
    void theSnapshotSurvivesALaterEditOfTheExercise() {
        Attempt attempt = gradeWolves(Optional.empty());
        attempts.save(attempt);
        entityManager.flush();
        entityManager.clear();

        jdbc.update("""
                update question_answer set value = 'river banks'
                where value = 'riverbanks' and question_id in (select id from question where exercise_id = ?)""", wolvesId());

        assertThat(attempts.findById(attempt.id()).orElseThrow().answers().get(3).expected()).containsExactly("riverbanks");
        assertThat(gradableExercises.findPublished(WOLVES).orElseThrow().questionNumbered(4).orElseThrow()
                .rule().accepted()).containsExactly("river banks");
    }

    @Test
    void anAttemptIsSavedOnlyOnce() {
        Attempt attempt = gradeWolves(Optional.empty());
        attempts.save(attempt);
        entityManager.flush();

        assertThatThrownBy(() -> attempts.save(attempt)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anUnknownLearnerIsRefused() {
        Attempt attempt = gradeWolves(Optional.of(new LearnerId(UUID.randomUUID())));

        assertThatThrownBy(() -> attempts.save(attempt)).isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------------- helpers

    private Attempt gradeWolves(Optional<LearnerId> learner) {
        return Attempt.grade(gradableExercises.findPublished(WOLVES).orElseThrow(),
                AnswerSheet.builder().answer(4, new GivenAnswer.Text("riverbanks")).build(),
                AZ, learner, Duration.ofSeconds(60), NOW, new Grader());
    }

    private long wolvesId() {
        return jdbc.queryForObject("select id from exercise where slug = ?", Long.class, WOLVES);
    }

    /** Adds group 3 to Wolves: one "Choose TWO" question (A and D) with options A–D. Returns the question id. */
    private long addChooseTwoGroup() {
        long group = jdbc.queryForObject("""
                insert into question_group (exercise_id, position, type_code, instruction_html, choose_count)
                values (?, 3, 'MCMA', 'Choose <strong>TWO</strong> letters.', 2) returning id""", Long.class, wolvesId());
        long question = jdbc.queryForObject("""
                insert into question (exercise_id, group_id, position, stem_text)
                values (?, ?, 1, 'Which TWO changes followed the return of the wolves?') returning id""",
                Long.class, wolvesId(), group);
        jdbc.update("""
                insert into question_option (question_id, option_key, label, position)
                values (?, 'A', 'Fewer elk', 1), (?, 'B', 'More tourists', 2), (?, 'C', 'Colder winters', 3),
                       (?, 'D', 'More beavers', 4)""", question, question, question, question);
        jdbc.update("insert into question_answer (question_id, value, position) values (?, 'A', 1), (?, 'D', 2)",
                question, question);
        return question;
    }

    private UUID addUser(String email) {
        return jdbc.queryForObject("""
                insert into app_user (email, ui_language, support_language) values (?, 'az', 'az') returning public_id""",
                UUID.class, email);
    }

    /** Every record component name in {@code type} and the records it contains. */
    private static List<String> fieldNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : type.getRecordComponents()) {
            names.add(type.getSimpleName() + "." + component.getName());
            for (Class<?> nested : recordsIn(component.getGenericType())) {
                names.addAll(fieldNames(nested));
            }
        }
        return names;
    }

    private static List<Class<?>> recordsIn(Type type) {
        List<Class<?>> records = new ArrayList<>();
        if (type instanceof Class<?> c && c.isRecord()) {
            records.add(c);
        } else if (type instanceof ParameterizedType p) {
            for (Type argument : p.getActualTypeArguments()) {
                records.addAll(recordsIn(argument));
            }
        }
        return records;
    }
}
