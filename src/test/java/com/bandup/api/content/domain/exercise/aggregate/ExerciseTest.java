package com.bandup.api.content.domain.exercise.aggregate;

import static com.bandup.api.content.domain.exercise.ExerciseFixtures.AZ;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.HTML;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.content;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.fill;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.group;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.option;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.options;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.question;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.tfng;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.type;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.wolves;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.bandup.api.content.domain.exercise.entity.Gloss;
import com.bandup.api.content.domain.exercise.entity.Question;
import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.content.domain.exercise.service.PublishPolicy;
import com.bandup.api.content.domain.exercise.valueobject.AnswerKey;
import com.bandup.api.content.domain.exercise.valueobject.EvidenceKey;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseId;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseStatus;
import com.bandup.api.content.domain.exercise.valueobject.MarkupKeys;
import com.bandup.api.content.domain.exercise.valueobject.QuestionFamily;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.Revision;
import com.bandup.api.content.domain.exercise.valueobject.Skill;
import com.bandup.api.content.domain.exercise.valueobject.Slug;
import com.bandup.api.content.domain.exercise.valueobject.Stem;
import com.bandup.api.content.domain.exercise.valueobject.WordLimit;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.numbering.NumberRange;

class ExerciseTest {

    private static final PublishPolicy POLICY = new PublishPolicy();
    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

    // ------------------------------------------------------------ draft

    @Test
    void aDraftStartsAtRevisionOneWithItsPassageInSyncWithTheHtml() {
        Exercise exercise = wolves();

        assertThat(exercise.status()).isEqualTo(ExerciseStatus.DRAFT);
        assertThat(exercise.revision()).isEqualTo(Revision.first());
        assertThat(exercise.version()).isZero();
        assertThat(exercise.publishedAt()).isEmpty();
        assertThat(exercise.title()).isEqualTo("The Return of the Wolves");
        assertThat(exercise.timer().orElseThrow().durationSeconds()).isEqualTo(300);
        assertThat(exercise.placement().orElseThrow().position()).isEqualTo(4);

        var passage = exercise.passage().orElseThrow();
        assertThat(passage.html().html()).isEqualTo(HTML);
        assertThat(passage.wordCount()).isEqualTo(12);
        // The editor's keys are kept as given, in document order, never renumbered.
        assertThat(passage.glosses()).extracting(g -> g.key().value()).containsExactly("g1", "g7");
        Gloss g1 = passage.glosses().getFirst();
        assertThat(g1.surface()).isEqualTo("eliminated");
        assertThat(g1.lemma()).contains("eliminate");
        assertThat(g1.meanings().get(AZ).orElseThrow().text()).isEqualTo("məhv edilmək");
        Gloss g7 = passage.glosses().get(1);
        assertThat(g7.surface()).isEqualTo("willows");
        assertThat(g7.lemma()).isEmpty();
        assertThat(g7.meanings().isEmpty()).isTrue();
        // A note for g9, which isn't marked in the HTML, is dropped.
        assertThat(passage.gloss(new com.bandup.api.content.domain.exercise.valueobject.GlossKey("g9"))).isEmpty();
        assertThat(passage.evidence()).extracting(EvidenceKey::value).containsExactly("e1", "e4");
    }

    @Test
    void aDraftMayBeIncomplete() {
        Exercise exercise = Exercise.draft(new ExerciseId(2), new Slug("half-done"), Skill.READING, LanguageCode.ENGLISH,
                content().passage(null, MarkupKeys.none()).noTimer().groups(group(10, type("MCQ", QuestionFamily.MC_SINGLE),
                        null, null, List.of(), question(101, new Stem.Text(""), null, AnswerKey.none()))).build());

        assertThat(exercise.passage()).isEmpty();
        assertThat(exercise.timer()).isEmpty();
        assertThat(exercise.groups().getFirst().questions().getFirst().answer().isEmpty()).isTrue();
    }

    @Test
    void reportsEveryKeyProblemAndAMissingTitleAtOnce() {
        MarkupKeys broken = new MarkupKeys(
                List.of(new MarkupKeys.GlossMark("g1", "a"), new MarkupKeys.GlossMark("x7", "b"), new MarkupKeys.GlossMark("g1", "c")),
                List.of("e1", "e1", "ev4"));

        assertThatThrownBy(() -> Exercise.draft(new ExerciseId(2), new Slug("broken"), Skill.READING, LanguageCode.ENGLISH,
                content().title(" ").passage(HTML, broken).groups(tfngWithoutE4()).build()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.violations()).extracting(Violation::message)
                        .containsExactlyInAnyOrder(
                                "Title is required.",
                                "Gloss key 'x7' must be 'g' followed by digits.",
                                "Gloss key 'g1' is used twice in the passage.",
                                "Evidence id 'e1' is used twice in the passage.",
                                "Evidence id 'ev4' must be 'e' followed by digits."));
    }

    @Test
    void aQuestionCantPointToEvidenceThatIsntInThePassage() {
        MarkupKeys withoutE4 = new MarkupKeys(MARKUP_GLOSSES, List.of("e1"));

        assertThatThrownBy(() -> Exercise.draft(new ExerciseId(2), new Slug("wolves"), Skill.READING, LanguageCode.ENGLISH,
                content().passage(HTML, withoutE4).build()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.violations()).containsExactlyInAnyOrder(
                        new Violation("questionGroups[0].questions[1].evidence", "Q2 points to evidence 'e4', which isn't in the passage."),
                        new Violation("questionGroups[1].questions[0].evidence", "Q3 points to evidence 'e4', which isn't in the passage.")));
    }

    @Test
    void optionsAndLimitsMustFitTheQuestionType() {
        QuestionGroup tfngWithExtras = group(10, type("TFNG", QuestionFamily.CHOICE_FIXED), 2, new WordLimit(2, false),
                options("A", "B"), question(101, new Stem.Text("x"), null, AnswerKey.of("TRUE"), option("A", "a")));
        QuestionGroup mcqWithRepeatedKeys = group(20, type("MCQ", QuestionFamily.MC_SINGLE), null, null, List.of(),
                question(201, new Stem.Text("y"), null, AnswerKey.of("A"), option("A", "a"), option("A", "b")));

        assertThatThrownBy(() -> wolves().editContent(content().groups(tfngWithExtras, mcqWithRepeatedKeys).build(), POLICY))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.violations()).extracting(Violation::field)
                        .containsExactlyInAnyOrder("questionGroups[0].chooseCount", "questionGroups[0].maxWords", "questionGroups[0].options",
                                "questionGroups[0].questions[0].options", "questionGroups[1].questions[0].options"));
    }

    // ------------------------------------------------------------- edit

    @Test
    void editingADraftReplacesTheContentAndKeepsTheRevision() {
        Exercise exercise = wolves();

        exercise.editContent(content().title("Wolves of Yellowstone").groups(fill(), tfng()).build(), POLICY);

        assertThat(exercise.title()).isEqualTo("Wolves of Yellowstone");
        assertThat(exercise.revision()).isEqualTo(Revision.first());
        // Reordered groups keep their questions' ids; numbers follow the new order.
        assertThat(exercise.numbering()).containsEntry(new QuestionId(201), new NumberRange(1, 1))
                .containsEntry(new QuestionId(101), new NumberRange(2, 2));
    }

    @Test
    void removingEvidenceThatAQuestionStillUsesIsRejected() {
        Exercise exercise = wolves();
        String withoutE4 = HTML.replace("<span class=\"ev\" id=\"e4\">", "<span>");

        assertThatThrownBy(() -> exercise.editContent(content().passage(withoutE4, new MarkupKeys(MARKUP_GLOSSES, List.of("e1"))).build(), POLICY))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.violations()).extracting(Violation::message)
                        .contains("Q2 points to evidence 'e4', which isn't in the passage."));
        assertThat(exercise.passage().orElseThrow().evidence()).hasSize(2); // unchanged
    }

    @Test
    void editingAPublishedExerciseRaisesTheRevisionAndMustStayPublishable() {
        Exercise exercise = wolves();
        exercise.publish(POLICY, NOW);

        exercise.editContent(content().title("Wolves, again").build(), POLICY);
        assertThat(exercise.revision()).isEqualTo(new Revision(2));
        assertThat(exercise.status()).isEqualTo(ExerciseStatus.PUBLISHED);

        QuestionGroup unanswered = group(10, type("TFNG", QuestionFamily.CHOICE_FIXED), null, null, List.of(),
                question(101, new Stem.Text("Wolves died of disease."), "e1", AnswerKey.none()));
        assertThatThrownBy(() -> exercise.editContent(content().title("Broken").groups(unanswered).build(), POLICY))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.violations()).extracting(Violation::field)
                        .containsExactly("questionGroups[0].questions[0].answer"));
        assertThat(exercise.title()).isEqualTo("Wolves, again");
        assertThat(exercise.revision()).isEqualTo(new Revision(2));
        assertThat(exercise.groups()).hasSize(2);
    }

    // ------------------------------------------------------------ lifecycle

    @Test
    void publishUnpublishAndArchive() {
        Exercise exercise = wolves();

        exercise.publish(POLICY, NOW);
        assertThat(exercise.status()).isEqualTo(ExerciseStatus.PUBLISHED);
        assertThat(exercise.publishedAt()).contains(NOW);

        exercise.publish(POLICY, NOW.plusSeconds(60)); // already published: nothing changes
        assertThat(exercise.publishedAt()).contains(NOW);

        exercise.unpublish();
        assertThat(exercise.status()).isEqualTo(ExerciseStatus.DRAFT);

        exercise.archive();
        assertThat(exercise.status()).isEqualTo(ExerciseStatus.ARCHIVED);
        assertThatThrownBy(() -> exercise.publish(POLICY, NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(exercise::unpublish).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> exercise.editContent(content().build(), POLICY))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.violations().getFirst().field()).isEqualTo("status"));
    }

    @Test
    void aFailedPublishChangesNothing() {
        Exercise exercise = Exercise.draft(new ExerciseId(2), new Slug("empty"), Skill.READING, LanguageCode.ENGLISH,
                content().passage(null, MarkupKeys.none()).groups().build());

        assertThatThrownBy(() -> exercise.publish(POLICY, NOW))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.violations()).extracting(Violation::field)
                        .containsExactlyInAnyOrder("passageHtml", "questions"));
        assertThat(exercise.status()).isEqualTo(ExerciseStatus.DRAFT);
        assertThat(exercise.publishedAt()).isEmpty();
    }

    // ------------------------------------------------------------ numbering

    @Test
    void chooseTwoTakesTwoNumbers() {
        QuestionGroup chooseTwo = group(30, type("MCMA", QuestionFamily.MC_MULTI), 2, null, List.of(),
                question(301, new Stem.Text("Which TWO?"), null, AnswerKey.of("A", "D"), option("A", "a"), option("B", "b"),
                        option("C", "c"), option("D", "d")));
        Exercise exercise = wolves();
        exercise.editContent(content().groups(tfng(), chooseTwo, fill()).build(), POLICY);

        assertThat(exercise.numbering().values()).extracting(NumberRange::toString).containsExactly("1", "2", "3–4", "5");
    }

    // ------------------------------------------------------------ identity & restore

    @Test
    void isEqualByIdAndRestoreRejectsBrokenData() {
        Exercise exercise = wolves();
        Exercise restored = Exercise.restore(exercise.id(), exercise.slug(), exercise.skill(), exercise.targetLanguage(),
                exercise.title(), exercise.format(), exercise.level().orElse(null), exercise.timer().orElse(null),
                exercise.placement().orElse(null), exercise.topics(), ExerciseStatus.PUBLISHED, new Revision(3), 7, NOW,
                exercise.passage().orElse(null), exercise.groups());

        assertThat(restored).isEqualTo(exercise).hasSameHashCodeAs(exercise);
        assertThat(restored.version()).isEqualTo(7);
        assertThat(restored.revision()).isEqualTo(new Revision(3));

        assertThatThrownBy(() -> Exercise.restore(exercise.id(), exercise.slug(), exercise.skill(), exercise.targetLanguage(),
                "", exercise.format(), null, null, null, Set.of(), ExerciseStatus.DRAFT, Revision.first(), 0, null,
                null, exercise.groups())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Exercise.restore(exercise.id(), exercise.slug(), exercise.skill(), exercise.targetLanguage(),
                "Two groups, one id", exercise.format(), null, null, null, Set.of(), ExerciseStatus.DRAFT, Revision.first(), 0,
                null, null, List.of(fill(), fill()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void childrenAreEqualByTheirIds() {
        Question original = tfng().questions().getFirst();
        Question edited = question(101, new Stem.Text("Different text"), null, AnswerKey.of("TRUE"));

        assertThat(edited).isEqualTo(original);
        assertThat(fill()).isEqualTo(fill());
    }

    private static final List<MarkupKeys.GlossMark> MARKUP_GLOSSES =
            List.of(new MarkupKeys.GlossMark("g1", "eliminated"), new MarkupKeys.GlossMark("g7", "willows"));

    private static QuestionGroup tfngWithoutE4() {
        return group(10, type("TFNG", QuestionFamily.CHOICE_FIXED), null, null, List.of(),
                question(101, new Stem.Text("Wolves died of disease."), null, AnswerKey.of("FALSE")));
    }
}
