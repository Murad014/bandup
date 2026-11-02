package com.bandup.api.grammar.domain.lesson.service;

import static com.bandup.api.grammar.domain.lesson.LessonFixtures.body;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.build;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.choose;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.en;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.fix;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.learn;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.replace;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.type;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.withCheck;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.withPractice;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LearnCard;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonText;
import com.bandup.api.grammar.domain.lesson.valueobject.Topic;
import com.bandup.api.grammar.domain.lesson.valueobject.WorkedExample;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

class LessonPolicyTest {

    private final LessonPolicy policy = new LessonPolicy(new AnswerNormaliser());

    private List<String> fields(LessonBody body) {
        return policy.check(body).stream().map(Violation::field).toList();
    }

    @Test
    void theB1LessonIsValid() {
        assertThat(policy.check(body())).isEmpty();
    }

    @Test
    void reportsEveryProblemAtOnceWithItsJsonPath() {
        LessonBody b = body();
        LessonBody broken = new LessonBody(b.level(), new Topic("Present Perfect", en("")), en(""), en("goal"), 3,
                null, learn().subList(0, 2), new WorkedExample(en("q"), List.of(en("only one")), " "),
                b.practice().subList(0, 1), b.check());
        assertThat(fields(broken)).containsExactlyInAnyOrder("topic.key", "topic.label", "title", "minutes", "learn",
                "worked.steps", "worked.answer", "practice");
    }

    @Test
    void textNeedsEnglishAndNoEmptyTranslationAndAMaximumLength() {
        LessonBody b = body();
        LessonText azOnly = new LessonText(Translations.of(Map.of(LanguageCode.of("az"), "Başlıq")));
        LessonText blankAz = new LessonText(Translations.of(Map.of(LanguageCode.ENGLISH, "Goal", LanguageCode.of("az"), " ")));
        LessonBody broken = new LessonBody(b.level(), new Topic("x1", en("x".repeat(81))), azOnly, blankAz, 20, null,
                b.learn(), b.worked(), b.practice(), b.check());
        assertThat(fields(broken)).containsExactlyInAnyOrder("topic.label", "title", "goal.az");
    }

    @Test
    void aBlankEnglishTextIsReportedOnce() {
        LessonBody b = body();
        LessonBody broken = new LessonBody(b.level(), b.topic(), en("  "), b.goal(), 20, null, b.learn(), b.worked(),
                b.practice(), b.check());
        assertThat(fields(broken)).containsExactly("title");
    }

    @Test
    void learnCardsNeedAnIconAndContentAndAtMostFiveStepsAndFourExamples() {
        LessonBody b = body();
        LearnCard empty = new LearnCard(" ", en("Label"), en("Heading"), null, List.of(), List.of(), null);
        LearnCard tooMuch = new LearnCard("🧭", en("Label"), en("Heading"), null,
                List.of(en("1"), en("2"), en("3"), en("4"), en("5"), en("6")),
                List.of(ex(), ex(), ex(), ex(), ex()), null);
        LessonBody broken = new LessonBody(b.level(), b.topic(), b.title(), b.goal(), 20, null,
                List.of(empty, tooMuch, learn().get(0)), b.worked(), b.practice(), b.check());
        assertThat(fields(broken)).containsExactlyInAnyOrder("learn[0].icon", "learn[0]", "learn[1].steps",
                "learn[1].examples");
    }

    private static LearnCard.Example ex() {
        return new LearnCard.Example(true, en("Example."));
    }

    @Test
    void itemIdsFollowTheirListAndAreUnique() {
        assertThat(fields(withCheck(replace(1, type("p1", "They ___ it.", List.of("won"), null)))))
                .containsExactly("check[1].id");
        assertThat(fields(withCheck(replace(1, type("c1", "They ___ it.", List.of("won"), null)))))
                .containsExactly("check[1].id");
        assertThat(fields(withPractice(replace(0, type("p0", "They ___ it.", List.of("won"), null)))))
                .containsExactly("practice[0].id");
    }

    @Test
    void theCheckNeedsFiveToFifteenItemsOfAtLeastTwoTypes() {
        List<Item> fiveTypes = List.of(
                type("c1", "A ___.", List.of("a"), null), type("c2", "B ___.", List.of("b"), null),
                type("c3", "C ___.", List.of("c"), null), type("c4", "D ___.", List.of("d"), null),
                type("c5", "E ___.", List.of("e"), null));
        assertThat(fields(withCheck(list -> fiveTypes))).containsExactly("check");
        assertThat(fields(withCheck(list -> list.subList(0, 4)))).containsExactly("check");
    }

    @Test
    void chooseNeedsTwoToFourDistinctOptionsAnAnswerAmongThemAndOneWhyPerOption() {
        assertThat(fields(withCheck(replace(0, choose("c1", "Pick ___.", List.of("a"), 0, List.of())))))
                .containsExactly("check[0].options");
        assertThat(fields(withCheck(replace(0, choose("c1", "Pick ___.", List.of("a", " A "), 0, List.of())))))
                .containsExactly("check[0].options");
        assertThat(fields(withCheck(replace(0, choose("c1", "Pick ___.", List.of("a", "an"), 2, List.of())))))
                .containsExactly("check[0].answer");
        assertThat(fields(withCheck(replace(0, choose("c1", "Pick ___.", List.of("a", "an"), -1, List.of())))))
                .containsExactly("check[0].answer");
        assertThat(fields(withCheck(replace(0, choose("c1", "Pick ___.", List.of("a", "an"), 1, List.of("one"))))))
                .containsExactly("check[0].whys");
        assertThat(fields(withCheck(replace(0, choose("c1", "Pick ___.", List.of("a", "an", "the"), 1, List.of())))))
                .isEmpty();
    }

    @Test
    void typeNeedsOneGapAndOneToFiveDistinctShortAnswers() {
        assertThat(fields(withCheck(replace(1, type("c2", "No gap here.", List.of("won"), null)))))
                .containsExactly("check[1].sentence");
        assertThat(fields(withCheck(replace(1, type("c2", "Two ___ gaps ___.", List.of("won"), null)))))
                .containsExactly("check[1].sentence");
        assertThat(fields(withCheck(replace(1, type("c2", "They ___ it.", List.of(), null)))))
                .containsExactly("check[1].accept");
        assertThat(fields(withCheck(replace(1, type("c2", "They ___ it.", List.of("won", "Won."), null)))))
                .containsExactly("check[1].accept");
        assertThat(fields(withCheck(replace(1, type("c2", "They ___ it.", List.of("would have been won it"), null)))))
                .containsExactly("check[1].accept");
        assertThat(fields(withCheck(replace(1, type("c2", "They ___ it.", List.of(" "), null)))))
                .containsExactly("check[1].accept");
        assertThat(fields(withCheck(replace(1, type("c2", "They ___ it.", List.of("won"), " ")))))
                .containsExactly("check[1].show");
    }

    @Test
    void fixNeedsAWrongSentenceThatIsNotAnAcceptedAnswer() {
        assertThat(fields(withCheck(replace(3, fix("c4", " ", List.of("saw"))))))
                .containsExactly("check[3].wrong");
        assertThat(fields(withCheck(replace(3, fix("c4", "I saw him.", List.of("i saw him"))))))
                .containsExactly("check[3].accept");
        assertThat(fields(withCheck(replace(3, fix("c4", "I have seen him yesterday.", List.of("I saw him yesterday"))))))
                .as("a whole corrected sentence may be longer than 4 words").isEmpty();
    }

    @Test
    void buildNeedsTheAnswersWordsAsMixedTiles() {
        assertThat(fields(withCheck(replace(4, build("c5", List.of("She", "left"), "She has left")))))
                .containsExactly("check[4].answer");
        assertThat(fields(withCheck(replace(4, build("c5", List.of("She", "has", "left"), "She has left.")))))
                .containsExactly("check[4].tiles");
        assertThat(fields(withCheck(replace(4, build("c5", List.of("left", "has", "she"), "She has left.")))))
                .as("case and final full stop don't matter").isEmpty();
        assertThat(fields(withCheck(replace(4, build("c5", List.of("x"), "x")))))
                .contains("check[4].tiles");
    }

    @Test
    void everyItemNeedsAPromptAndAnExplanation() {
        Item noText = new Item.Fix(new ItemId("c4"), en(""), "I have seen him yesterday.", List.of("saw"), null, en(""));
        assertThat(fields(withCheck(replace(3, noText)))).containsExactlyInAnyOrder("check[3].prompt",
                "check[3].explanation");
    }
}
