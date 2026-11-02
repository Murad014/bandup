package com.bandup.api.grammar.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;

import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.grammar.domain.lesson.valueobject.LearnCard;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonText;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;

/** Builds the admin view of a lesson. */
@UtilityClass
final class LessonViews {

    static AdminLessonView admin(GrammarLesson lesson, long version) {
        LessonBody b = lesson.body();
        return new AdminLessonView(lesson.slug().value(), version, lesson.status().name(), lesson.revision().value(),
                lesson.position().map(Position::value).orElse(null), lesson.publishedAt().orElse(null),
                b.level().name(), new AdminLessonView.Topic(b.topic().key(), text(b.topic().label())), text(b.title()),
                text(b.goal()), b.minutes(), b.learn().stream().map(LessonViews::card).toList(),
                new AdminLessonView.Worked(text(b.worked().question()), texts(b.worked().steps()), b.worked().answer()),
                b.practice().stream().map(LessonViews::item).toList(), b.check().stream().map(LessonViews::item).toList());
    }

    private static AdminLessonView.LearnCard card(LearnCard card) {
        return new AdminLessonView.LearnCard(card.icon(), text(card.label()), text(card.heading()),
                card.bodyText().map(LessonViews::text).orElse(null), texts(card.steps()),
                card.examples().stream().map(e -> new AdminLessonView.Example(e.ok(), text(e.text()))).toList(),
                card.tipText().map(LessonViews::text).orElse(null));
    }

    private static AdminLessonView.Item item(Item item) {
        Map<String, String> prompt = text(item.prompt());
        Map<String, String> explanation = text(item.explanation());
        String id = item.id().value();
        return switch (item) {
            case Item.Choose c -> new AdminLessonView.Item(id, c.code(), prompt, null, null, c.options(), c.answer(),
                    texts(c.whys()), null, null, null, explanation);
            case Item.Type t -> new AdminLessonView.Item(id, t.code(), prompt, t.sentence(), null, null, null, null,
                    t.accept(), t.show(), null, explanation);
            case Item.Fix f -> new AdminLessonView.Item(id, f.code(), prompt, null, f.wrong(), null, null, null,
                    f.accept(), f.show(), null, explanation);
            case Item.Build bl -> new AdminLessonView.Item(id, bl.code(), prompt, null, null, null, bl.answer(), null,
                    null, null, bl.tiles(), explanation);
        };
    }

    static Map<String, String> text(LessonText text) {
        Map<String, String> values = new LinkedHashMap<>();
        text.values().asMap().forEach((language, value) -> values.put(language.value(), value));
        return values;
    }

    private static List<Map<String, String>> texts(List<LessonText> texts) {
        return texts.stream().map(LessonViews::text).toList();
    }
}
