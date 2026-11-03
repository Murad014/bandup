package com.bandup.api.vocabulary.domain.word;

import java.util.List;

import com.bandup.api.vocabulary.domain.word.service.CardPolicy;
import com.bandup.api.vocabulary.domain.word.service.WordNormaliser;
import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;
import com.bandup.api.vocabulary.domain.word.valueobject.GapTask;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.Meaning;
import com.bandup.api.vocabulary.domain.word.valueobject.PairTask;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

/** A valid card (the card-format.md example) for the tests. */
public final class Cards {

    public static final CardPolicy POLICY = new CardPolicy(new WordNormaliser());
    public static final Lemma LEMMA = new Lemma("riverbank");
    public static final List<WordForm> FORMS = List.of(new WordForm("riverbank"), new WordForm("riverbanks"));

    private Cards() {
    }

    public static CardContent riverbank() {
        return new CardContent("noun", "B1", "/ˈrɪvəbæŋk/", new Meaning("the ground at the side of a river", "çay sahili"),
                List.of("Willows grew back along the riverbanks.", "We walked along the riverbank after lunch."),
                List.of("along the riverbank", "a steep riverbank"), List.of("river (noun)", "bank (noun)"),
                new GapTask("The children sat on the ___ and watched the boats.", "riverbank"),
                new PairTask("They built a path along the ___.", List.of("riverbank", "erosion", "predator", "decline"), 0),
                List.of("a place where people keep their money", "the bottom of a lake", "a small boat used on rivers"));
    }

    /** A valid card for any one-word lemma (its forms: the lemma and lemma + "s"). */
    public static CardContent of(String lemma) {
        return new CardContent("noun", "B2", "/" + lemma + "/", new Meaning(meaningOf(lemma),
                "az " + lemma), List.of("This sentence uses " + lemma + " once.", "Here is " + lemma + " in a second sentence."),
                List.of("a big " + lemma), List.of(), new GapTask("We talked about the ___ for an hour.", lemma),
                new PairTask("She found a new ___ in the box.", List.of(lemma, "table", "window", "garden"), 0),
                List.of("a kind of fruit for " + lemma, "a small river for " + lemma, "a type of music for " + lemma));
    }

    /** A meaning that differs for every lemma and never contains it. */
    public static String meaningOf(String lemma) {
        return "the meaning given for test word number " + Math.floorMod(lemma.hashCode(), 100000);
    }

    public static List<WordForm> formsOf(String lemma) {
        return List.of(new WordForm(lemma), new WordForm(lemma + "s"));
    }

    public static CardContent with(java.util.function.UnaryOperator<CardContentBuilder> change) {
        return change.apply(new CardContentBuilder(riverbank())).build();
    }

    /** Changes one field of a card. */
    public static final class CardContentBuilder {
        String pos, level, ipa;
        Meaning meaning;
        List<String> examples, collocations, family, wrongMeanings;
        GapTask gap;
        PairTask pair;

        CardContentBuilder(CardContent c) {
            pos = c.pos(); level = c.level(); ipa = c.ipa(); meaning = c.meaning(); examples = c.examples();
            collocations = c.collocations(); family = c.family(); gap = c.gap(); pair = c.pair(); wrongMeanings = c.wrongMeanings();
        }

        public CardContentBuilder pos(String v) { pos = v; return this; }
        public CardContentBuilder level(String v) { level = v; return this; }
        public CardContentBuilder ipa(String v) { ipa = v; return this; }
        public CardContentBuilder meaning(String en, String az) { meaning = new Meaning(en, az); return this; }
        public CardContentBuilder examples(String... v) { examples = List.of(v); return this; }
        public CardContentBuilder collocations(String... v) { collocations = List.of(v); return this; }
        public CardContentBuilder family(String... v) { family = List.of(v); return this; }
        public CardContentBuilder wrongMeanings(String... v) { wrongMeanings = List.of(v); return this; }
        public CardContentBuilder gap(String sentence, String answer) { gap = new GapTask(sentence, answer); return this; }
        public CardContentBuilder pair(String sentence, List<String> options, int answer) { pair = new PairTask(sentence, options, answer); return this; }

        CardContent build() {
            return new CardContent(pos, level, ipa, meaning, examples, collocations, family, gap, pair, wrongMeanings);
        }
    }
}
