package com.bandup.api.grammar.domain.lesson.valueobject;

import static com.bandup.api.grammar.domain.lesson.LessonFixtures.build;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.choose;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.en;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.enAz;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.fix;
import static com.bandup.api.grammar.domain.lesson.LessonFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bandup.api.grammar.domain.lesson.service.AnswerNormaliser;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.language.LanguageCode;

class ItemTest {

    private final AnswerNormaliser normaliser = new AnswerNormaliser();
    private final LanguageChain english = LanguageChain.englishOnly();

    private ItemFeedback grade(Item item, GivenAnswer answer) {
        return item.grade(answer, normaliser, english);
    }

    @Test
    void chooseIsCorrectOnlyForTheAnswerIndexAndExplainsThePickedWrongOption() {
        Item item = choose("c1", "I ___ to Japan.", List.of("went", "have been"), 1, List.of("needs a time", "✓"));
        assertThat(grade(item, new GivenAnswer.Choice(1))).isEqualTo(new ItemFeedback(true, "have been", null, "Explained."));
        assertThat(grade(item, new GivenAnswer.Choice(0))).isEqualTo(
                new ItemFeedback(false, "have been", "needs a time", "Explained."));
    }

    @Test
    void chooseWithoutWhysGivesNoWhy() {
        Item item = choose("c1", "Pick ___.", List.of("a", "an"), 1, List.of());
        assertThat(grade(item, new GivenAnswer.Choice(0)).whyText()).isEmpty();
    }

    @Test
    void chooseRefusesAnIndexOutsideTheOptionsAndOtherAnswerKinds() {
        Item item = choose("c1", "Pick ___.", List.of("a", "an"), 1, List.of());
        for (GivenAnswer bad : List.of(new GivenAnswer.Choice(2), new GivenAnswer.Choice(-1), new GivenAnswer.Text("an"))) {
            assertThatThrownBy(() -> grade(item, bad)).isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).violations().get(0).field()).isEqualTo("answer"));
        }
    }

    @Test
    void typeAcceptsAnyAcceptedValueAfterNormalising() {
        Item item = type("c3", "We ___ her since Monday.", List.of("haven't seen", "have not seen"), "haven't seen");
        assertThat(grade(item, new GivenAnswer.Text("  Haven’t SEEN. ")).correct()).isTrue();
        assertThat(grade(item, new GivenAnswer.Text("have not  seen")).correct()).isTrue();
        ItemFeedback wrong = grade(item, new GivenAnswer.Text("didn't see"));
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.expected()).isEqualTo("haven't seen");
    }

    @Test
    void theShownAnswerDefaultsToTheFirstAcceptedOne() {
        assertThat(grade(type("c2", "They ___ it.", List.of("won", "did win"), null), new GivenAnswer.Text("x")).expected())
                .isEqualTo("won");
        assertThat(grade(fix("c4", "I have seen him yesterday.", List.of("saw")), new GivenAnswer.Text("seen")).expected())
                .isEqualTo("saw");
    }

    @Test
    void typedItemsRefuseABlankAnswerOrAnotherKind() {
        Item item = fix("c4", "I have seen him yesterday.", List.of("saw"));
        assertThatThrownBy(() -> grade(item, new GivenAnswer.Text(" . "))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> grade(item, new GivenAnswer.Choice(0))).isInstanceOf(DomainException.class);
    }

    @Test
    void buildIsCorrectWhenTheTilesMakeTheAnswer() {
        Item item = build("c5", List.of("just", "She", "left", "has"), "She has just left.");
        assertThat(grade(item, new GivenAnswer.Tiles(List.of("She", "has", "just", "left"))).correct()).isTrue();
        ItemFeedback wrong = grade(item, new GivenAnswer.Tiles(List.of("She", "just", "has", "left")));
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.expected()).isEqualTo("She has just left.");
    }

    @Test
    void buildRefusesTilesThatAreMissingRepeatedOrForeign() {
        Item item = build("c5", List.of("just", "She", "left", "has"), "She has just left");
        for (List<String> bad : List.of(List.of("She", "has", "left"), List.of("She", "has", "has", "left"),
                List.of("She", "has", "just", "gone"), List.of("She", "has", "just", "left", "now"))) {
            assertThatThrownBy(() -> grade(item, new GivenAnswer.Tiles(bad))).isInstanceOf(DomainException.class);
        }
        assertThatThrownBy(() -> grade(item, new GivenAnswer.Text("She has just left"))).isInstanceOf(DomainException.class);
    }

    @Test
    void feedbackIsInTheLearnersLanguageWithAnEnglishFallback() {
        Item item = new Item.Choose(new ItemId("c1"), en("Pick ___."), List.of("a", "an"), 1,
                List.of(enAz("consonant sound", "samit səs"), en("✓")), enAz("Vowel sound → an.", "Sait səs → an."));
        LanguageChain azerbaijani = LanguageChain.of(List.of(LanguageCode.of("az"), LanguageCode.ENGLISH));
        assertThat(item.grade(new GivenAnswer.Choice(0), normaliser, azerbaijani))
                .isEqualTo(new ItemFeedback(false, "an", "samit səs", "Sait səs → an."));
        Item englishOnly = type("c2", "They ___ it.", List.of("won"), null);
        assertThat(englishOnly.grade(new GivenAnswer.Text("won"), normaliser, azerbaijani).explanation())
                .isEqualTo("Explained.");
    }
}
