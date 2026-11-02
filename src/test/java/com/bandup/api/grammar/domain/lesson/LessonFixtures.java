package com.bandup.api.grammar.domain.lesson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LearnCard;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonText;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;
import com.bandup.api.grammar.domain.lesson.valueobject.Topic;
import com.bandup.api.grammar.domain.lesson.valueobject.WorkedExample;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

/** A valid B1 lesson (present perfect vs past simple, from the prototype) and builders for its parts. */
public final class LessonFixtures {

    private LessonFixtures() {
    }

    public static LessonText en(String text) {
        return LessonText.english(text);
    }

    public static LessonText enAz(String en, String az) {
        return new LessonText(Translations.of(Map.of(LanguageCode.ENGLISH, en, LanguageCode.of("az"), az)));
    }

    public static Item.Choose choose(String id, String prompt, List<String> options, int answer, List<String> whys) {
        return new Item.Choose(new ItemId(id), en(prompt), options, answer, whys.stream().map(LessonText::english).toList(),
                en("Explained."));
    }

    public static Item.Type type(String id, String sentence, List<String> accept, String show) {
        return new Item.Type(new ItemId(id), en("Type the correct form:"), sentence, accept, show, en("Explained."));
    }

    public static Item.Fix fix(String id, String wrong, List<String> accept) {
        return new Item.Fix(new ItemId(id), en("This sentence is wrong. Type the correct verb:"), wrong, accept, null,
                en("Explained."));
    }

    public static Item.Build build(String id, List<String> tiles, String answer) {
        return new Item.Build(new ItemId(id), en("Build the sentence:"), tiles, answer, en("Explained."));
    }

    public static List<LearnCard> learn() {
        return List.of(
                new LearnCard("🌉", en("The idea"), en("Present perfect links past to NOW"),
                        en("<b>have/has + past participle</b>."), List.of(),
                        List.of(new LearnCard.Example(true, en("I <b>have lost</b> my keys."))), null),
                new LearnCard("📅", en("Past simple"), en("Finished action, finished time"),
                        en("Signals: <b>yesterday, ago</b>."), List.of(), List.of(), null),
                new LearnCard("🧭", en("How to decide"), en("Ask 2 questions, in order"), null,
                        List.of(en("1️⃣ Finished-time word? → <b>Past simple</b>"),
                                en("2️⃣ since / for / ever? → <b>Present perfect</b>")),
                        List.of(), en("Check the time words first.")));
    }

    public static WorkedExample worked() {
        return new WorkedExample(en("Choose the verb: “I ___ (visit) London in 2019.”"),
                List.of(en("🔍 Find the time clue: <b>in 2019</b>."), en("✅ Finished time → <b>visited</b>.")), "visited");
    }

    public static List<Item> practice() {
        return List.of(
                choose("p1", "I ___ my wallet — I can't find it!", List.of("lost", "have lost"), 1,
                        List.of("the result matters now", "✓ result affects now")),
                type("p2", "She ___ her homework yesterday.", List.of("finished"), null));
    }

    public static List<Item> check() {
        return List.of(
                choose("c1", "I ___ to Japan twice in my life.", List.of("went", "have been", "have gone", "was"), 1,
                        List.of("needs a finished time", "✓ life experience", "means still away", "can't take “to”")),
                type("c2", "They ___ the title in 2021.", List.of("won"), "won"),
                type("c3", "We ___ her since Monday.", List.of("haven't seen", "have not seen"), "haven't seen"),
                fix("c4", "I have seen him yesterday.", List.of("saw")),
                build("c5", List.of("since", "We", "him", "have", "2019", "known"), "We have known him since 2019"));
    }

    public static LessonBody body() {
        return new LessonBody(GrammarLevel.B1, new Topic("present-perfect", en("Present perfect vs past simple")),
                en("Present Perfect vs Past Simple"), en("By the end, you'll know how to decide."), 20, null,
                learn(), worked(), practice(), check());
    }

    public static LessonBody withPosition(LessonBody body, int position) {
        return new LessonBody(body.level(), body.topic(), body.title(), body.goal(), body.minutes(),
                new Position(position), body.learn(), body.worked(), body.practice(), body.check());
    }

    public static LessonBody withCheck(UnaryOperator<List<Item>> change) {
        LessonBody body = body();
        return new LessonBody(body.level(), body.topic(), body.title(), body.goal(), body.minutes(), body.position(),
                body.learn(), body.worked(), body.practice(), change.apply(new ArrayList<>(body.check())));
    }

    public static LessonBody withPractice(UnaryOperator<List<Item>> change) {
        LessonBody body = body();
        return new LessonBody(body.level(), body.topic(), body.title(), body.goal(), body.minutes(), body.position(),
                body.learn(), body.worked(), change.apply(new ArrayList<>(body.practice())), body.check());
    }

    /** {@code list} with item {@code index} replaced. */
    public static UnaryOperator<List<Item>> replace(int index, Item item) {
        return list -> {
            list.set(index, item);
            return list;
        };
    }
}
