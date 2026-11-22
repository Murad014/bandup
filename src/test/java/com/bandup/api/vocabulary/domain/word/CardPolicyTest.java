package com.bandup.api.vocabulary.domain.word;

import static com.bandup.api.vocabulary.domain.word.Cards.FORMS;
import static com.bandup.api.vocabulary.domain.word.Cards.LEMMA;
import static com.bandup.api.vocabulary.domain.word.Cards.POLICY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.vocabulary.domain.word.Cards.CardContentBuilder;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

/** Every rule of card-format.md, each with its JSON path. */
class CardPolicyTest {

    @Test
    void theExampleCardIsValid() {
        assertThat(POLICY.check(LEMMA, FORMS, Cards.riverbank())).isEmpty();
    }

    @Test
    void lemmaAndForms() {
        assertThat(fields(POLICY.check(new Lemma("River Bank!"), FORMS, Cards.riverbank()))).contains("lemma");
        assertThat(fields(POLICY.check(LEMMA, List.of(new WordForm("riverbanks")), Cards.riverbank()))).contains("forms");
        assertThat(fields(POLICY.check(LEMMA, List.of(), Cards.riverbank()))).contains("forms");
    }

    /** Bug: "vicious cycle" couldn't be added — the AI blanked one word of the phrase and the card failed. */
    @Test
    void aPhraseMayBlankOneOfItsWordsWhenTheSentenceMakesThePhrase() {
        Lemma lemma = new Lemma("vicious circle");
        List<WordForm> forms = List.of(new WordForm("vicious circle"), new WordForm("vicious circles"),
                new WordForm("vicious cycle"));
        assertThat(POLICY.check(lemma, forms, phrase("Debt can become a ___ circle for poor families.", "vicious",
                "They were stuck in a vicious ___ of debt.", 0))).isEmpty();
        assertThat(POLICY.check(lemma, forms, phrase("Many families are caught in a ___ every year.", "vicious circle",
                "They were stuck in a vicious ___ of debt.", 0))).isEmpty();

        assertThat(fields(POLICY.check(lemma, forms, phrase("A vicious circle of ___ hurt the family.", "debt",
                "They were stuck in a vicious ___ of debt.", 0)))).containsExactly("gap.answer");
        assertThat(fields(POLICY.check(lemma, forms, phrase("They felt ___ about the news today.", "vicious",
                "They were stuck in a vicious ___ of debt.", 0)))).containsExactly("gap.answer");
        assertThat(fields(POLICY.check(lemma, forms, phrase("Debt can become a ___ circle for poor families.", "vicious",
                "They were stuck in a vicious circle of ___.", 0)))).containsExactly("pair.answer");
    }

    private static com.bandup.api.vocabulary.domain.word.valueobject.CardContent phrase(String gap, String gapAnswer,
            String pair, int pairAnswer) {
        return new com.bandup.api.vocabulary.domain.word.valueobject.CardContent("phrase", "C1", "/ˌvɪʃəs ˈsɜːkl/",
                new com.bandup.api.vocabulary.domain.word.valueobject.Meaning(
                        "a bad situation that keeps making itself worse", "qapalı dairə"),
                List.of("Poverty and poor health form a vicious circle.", "We must break this vicious circle soon."),
                List.of("break the vicious circle", "a vicious circle of debt"), List.of(),
                new com.bandup.api.vocabulary.domain.word.valueobject.GapTask(gap, gapAnswer),
                new com.bandup.api.vocabulary.domain.word.valueobject.PairTask(pair,
                        List.of("circle", "debt", "window", "garden"), pairAnswer),
                List.of("a round shape drawn on paper", "a friendly group of people", "a fast way to travel"));
    }

    @Test
    void eachFieldIsChecked() {
        assertProblem(c -> c.pos("thing"), "pos");
        assertProblem(c -> c.level("D1"), "level");
        assertProblem(c -> c.ipa("ˈrɪvəbæŋk"), "ipa");
        assertProblem(c -> c.meaning("", "çay sahili"), "meaning.en");
        assertProblem(c -> c.meaning("a riverbank by the water", "çay sahili"), "meaning.en");
        assertProblem(c -> c.meaning("the ground at the side of a river", ""), "meaning.az");
        assertProblem(c -> c.examples("Willows grew back along the riverbanks."), "examples");
        assertProblem(c -> c.examples("Willows grew back along the river.", "We walked along the riverbank after lunch."),
                "examples[0]");
        assertProblem(c -> c.examples("Willows grew back along the <b>riverbanks</b>.", "We walked along the riverbank after lunch."),
                "examples[0]");
        assertProblem(c -> c.collocations(), "collocations");
        assertProblem(c -> c.collocations("a steep hill"), "collocations[0]");
        assertProblem(c -> c.family("a", "b", "c", "d", "e"), "family");
        assertProblem(c -> c.gap("The children sat on the bank and watched.", "riverbank"), "gap.sentence");
        assertProblem(c -> c.gap("The children sat on the ___ and watched ___.", "riverbank"), "gap.sentence");
        assertProblem(c -> c.gap("The children sat on the ___ and watched the boats.", "river"), "gap.answer");
        assertProblem(c -> c.pair("They built a path along the ___.", List.of("riverbank", "erosion", "predator"), 0), "pair.options");
        assertProblem(c -> c.pair("They built a path along the ___.", List.of("riverbank", "Riverbank", "predator", "decline"), 0),
                "pair.options");
        assertProblem(c -> c.pair("They built a path along the ___.", List.of("riverbank", "erosion", "predator", "decline"), 4),
                "pair.answer");
        assertProblem(c -> c.pair("They built a path along the ___.", List.of("riverbank", "erosion", "predator", "decline"), 1),
                "pair.answer");
        assertProblem(c -> c.wrongMeanings("a", "b"), "wrongMeanings");
        assertProblem(c -> c.wrongMeanings("the ground at the side of a river", "the bottom of a lake", "a small boat"),
                "wrongMeanings");
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        List<Violation> problems = POLICY.check(LEMMA, FORMS, Cards.with(c -> c.pos("x").level("x").ipa("x")));
        assertThat(fields(problems)).contains("pos", "level", "ipa");
    }

    @Test
    void wordsAreFoundAsWholeWords() {
        assertThat(POLICY.containsWord("Along the Riverbanks, again.", FORMS)).isTrue();
        assertThat(POLICY.containsWord("The riverbanker came.", FORMS)).isFalse();
    }

    private static void assertProblem(UnaryOperator<CardContentBuilder> change, String field) {
        assertThat(fields(POLICY.check(LEMMA, FORMS, Cards.with(change)))).as(field).contains(field);
    }

    private static List<String> fields(List<Violation> problems) {
        return problems.stream().map(Violation::field).toList();
    }
}
