package com.bandup.api.content.domain.exercise.service;

import static com.bandup.api.content.domain.exercise.ExerciseFixtures.content;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.group;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.option;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.options;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.question;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.type;
import static com.bandup.api.content.domain.exercise.ExerciseFixtures.wolves;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bandup.api.content.domain.exercise.aggregate.Exercise;
import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.content.domain.exercise.valueobject.AnswerKey;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseId;
import com.bandup.api.content.domain.exercise.valueobject.MarkupKeys;
import com.bandup.api.content.domain.exercise.valueobject.MediaId;
import com.bandup.api.content.domain.exercise.valueobject.QuestionFamily;
import com.bandup.api.content.domain.exercise.valueobject.Skill;
import com.bandup.api.content.domain.exercise.valueobject.Slug;
import com.bandup.api.content.domain.exercise.valueobject.Stem;
import com.bandup.api.content.domain.exercise.valueobject.WordLimit;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;

class PublishPolicyTest {

    private final PublishPolicy policy = new PublishPolicy();

    @Test
    void theWolvesContentIsPublishable() {
        assertThat(policy.check(wolves())).isEmpty();
    }

    @Test
    void readingNeedsAPassageAndEveryExerciseAQuestion() {
        Exercise empty = draft(content().passage("<p> </p>", MarkupKeys.none()).groups());
        assertThat(messages(empty)).containsExactlyInAnyOrder("Add the passage text before publishing.",
                "Add at least one question before publishing.");

        Exercise grammar = Exercise.draft(new ExerciseId(3), new Slug("grammar"), Skill.GRAMMAR, LanguageCode.ENGLISH,
                content().passage(null, MarkupKeys.none()).groups(choiceFixed("TRUE")).build());
        assertThat(policy.check(grammar)).isEmpty();
    }

    @Test
    void groupsNeedQuestionsAndAnInstruction() {
        QuestionGroup emptyGroup = new QuestionGroup(new com.bandup.api.content.domain.exercise.valueobject.GroupId(10), type("TFNG", QuestionFamily.CHOICE_FIXED),
                new com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml("<b></b>"), com.bandup.api.shared.domain.language.Translations.empty(),
                null, null, false, null, List.of(), List.of());
        assertThat(fields(draft(content().groups(emptyGroup, choiceFixedAt(20, "TRUE")))))
                .containsExactlyInAnyOrder("questionGroups[0]", "questionGroups[0].instruction");
    }

    @Test
    void trueFalseNotGivenAnswersComeFromTheFixedSet() {
        assertThat(messages(draft(content().groups(choiceFixed("NOT GIVEN"))))).isEmpty();
        assertThat(messages(draft(content().groups(choiceFixed("MAYBE"))))).containsExactly("Q1 must be one of TRUE / FALSE / NOT GIVEN.");
        assertThat(messages(draft(content().groups(choiceFixed())))).containsExactly("Q1 must be one of TRUE / FALSE / NOT GIVEN.");
        QuestionGroup ynng = group(10, type("YNNG", QuestionFamily.CHOICE_FIXED), null, null, List.of(),
                question(101, new Stem.Text("x"), null, AnswerKey.of("TRUE")));
        assertThat(messages(draft(content().groups(ynng)))).containsExactly("Q1 must be one of YES / NO / NOT GIVEN.");
    }

    @Test
    void gapAnswersMustExistAndFitTheWordLimit() {
        QuestionGroup gap = group(10, type("FILL", QuestionFamily.GAP), null, new WordLimit(2, true), List.of(),
                question(101, new Stem.Gap("before", "after"), null, AnswerKey.of("17 grey wolves", "the grey wolves")),
                question(102, new Stem.Gap("before", null), null, AnswerKey.none()));

        assertThat(messages(draft(content().groups(gap)))).containsExactlyInAnyOrder(
                "Q1: 'the grey wolves' is longer than the word limit.",
                "Q2 needs at least one accepted answer.");
    }

    @Test
    void multipleChoiceNeedsOptionsAndValidKeys() {
        QuestionGroup mcq = group(10, type("MCQ", QuestionFamily.MC_SINGLE), null, null, List.of(),
                question(101, new Stem.Text("Which?"), null, AnswerKey.of("B"), option("A", "a"), option("B", "b")),
                question(102, new Stem.Text("Which?"), null, AnswerKey.of("C"), option("A", "a")));

        assertThat(messages(draft(content().groups(mcq)))).containsExactlyInAnyOrder(
                "Q2 needs at least two options.", "Q2 must select one of its options.");
    }

    @Test
    void chooseNNeedsNKeysThatExistAndMoreThanNOptions() {
        QuestionGroup chooseTwo = group(10, type("MCMA", QuestionFamily.MC_MULTI), 2, null, List.of(),
                question(101, new Stem.Text("ok"), null, AnswerKey.of("A", "D"), options("A", "B", "C", "D").toArray(new com.bandup.api.content.domain.exercise.valueobject.Option[0])),
                question(102, new Stem.Text("three"), null, AnswerKey.of("A", "B", "C"), options("A", "B", "C", "D").toArray(new com.bandup.api.content.domain.exercise.valueobject.Option[0])),
                question(103, new Stem.Text("ghost"), null, AnswerKey.of("A", "Z"), options("A", "B").toArray(new com.bandup.api.content.domain.exercise.valueobject.Option[0])));
        QuestionGroup noCount = group(20, type("MCMA", QuestionFamily.MC_MULTI), null, null, List.of(),
                question(201, new Stem.Text("?"), null, AnswerKey.of("A", "B"), options("A", "B", "C").toArray(new com.bandup.api.content.domain.exercise.valueobject.Option[0])));

        assertThat(messages(draft(content().groups(chooseTwo, noCount)))).containsExactlyInAnyOrder(
                "Q3–4 must select exactly 2 options.",
                "Q5–6 needs more than 2 options.",
                "Q5–6 selects an option that doesn't exist.",
                "Say how many answers to choose.");
    }

    @Test
    void matchingUsesTheBankAndEachOptionOnceUnlessReusable() {
        QuestionGroup headings = group(10, type("MATCH_HEADINGS", QuestionFamily.MATCH), null, false, null,
                options("i", "ii", "iii"),
                question(101, new Stem.Text("Paragraph A"), null, AnswerKey.of("ii")),
                question(102, new Stem.Text("Paragraph B"), null, AnswerKey.of("ii")),
                question(103, new Stem.Text("Paragraph C"), null, AnswerKey.of("iv")));
        assertThat(messages(draft(content().groups(headings)))).containsExactlyInAnyOrder(
                "Q2 uses 'ii', already used by Q1; each option can be used once.",
                "Q3 must match one of the group's options.");

        QuestionGroup features = group(10, type("MATCH_FEATURES", QuestionFamily.MATCH), null, true, null,
                options("A", "B"),
                question(101, new Stem.Text("x"), null, AnswerKey.of("A")),
                question(102, new Stem.Text("y"), null, AnswerKey.of("A")));
        assertThat(messages(draft(content().groups(features)))).isEmpty();

        QuestionGroup tinyBank = group(10, type("MATCH_INFO", QuestionFamily.MATCH), null, true, null, options("A"),
                question(101, new Stem.Text("x"), null, AnswerKey.of("A")));
        assertThat(messages(draft(content().groups(tinyBank)))).containsExactly("Add at least two options to choose from.");
    }

    @Test
    void aDiagramNeedsItsPictureAndEveryQuestionItsText() {
        QuestionGroup diagram = group(10, type("DIAGRAM_LABEL", QuestionFamily.GAP), null, false, null, List.of(),
                question(101, new Stem.Text(" "), null, AnswerKey.of("valve")));
        assertThat(messages(draft(content().groups(diagram)))).containsExactlyInAnyOrder(
                "A diagram question needs its picture.", "Q1 has no text.");

        QuestionGroup withPicture = group(10, type("DIAGRAM_LABEL", QuestionFamily.GAP), null, false, new MediaId(5), List.of(),
                question(101, new Stem.Text("Label 1"), null, AnswerKey.of("valve")));
        assertThat(messages(draft(content().groups(withPicture)))).isEmpty();
    }

    private Exercise draft(com.bandup.api.content.domain.exercise.ExerciseFixtures.Builder builder) {
        return Exercise.draft(new ExerciseId(2), new Slug("draft"), Skill.READING, LanguageCode.ENGLISH, builder.build());
    }

    private List<String> messages(Exercise exercise) {
        return policy.check(exercise).stream().map(Violation::message).toList();
    }

    private List<String> fields(Exercise exercise) {
        return policy.check(exercise).stream().map(Violation::field).toList();
    }

    private static QuestionGroup choiceFixed(String... answer) {
        return choiceFixedAt(10, answer);
    }

    private static QuestionGroup choiceFixedAt(long groupId, String... answer) {
        return group(groupId, type("TFNG", QuestionFamily.CHOICE_FIXED), null, null, List.of(),
                question(groupId * 10 + 1, new Stem.Text("A statement."), null, new AnswerKey(List.of(answer))));
    }
}
