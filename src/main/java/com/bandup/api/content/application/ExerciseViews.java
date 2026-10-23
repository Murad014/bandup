package com.bandup.api.content.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lombok.experimental.UtilityClass;

import com.bandup.api.content.domain.exercise.aggregate.Exercise;
import com.bandup.api.content.domain.exercise.entity.Question;
import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.content.domain.exercise.valueobject.Explanation;
import com.bandup.api.content.domain.exercise.valueobject.GlossMeaning;
import com.bandup.api.content.domain.exercise.valueobject.Option;
import com.bandup.api.content.domain.exercise.valueobject.Placement;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.Stem;
import com.bandup.api.content.domain.exercise.valueobject.Timer;
import com.bandup.api.content.domain.exercise.valueobject.WordLimit;
import com.bandup.api.shared.domain.numbering.NumberRange;

/** Builds the admin view of an exercise. */
@UtilityClass
final class ExerciseViews {

    /** @param coverUrl the cover's address, or null */
    static AdminExerciseView of(Exercise e, long version, String coverUrl) {
        Map<QuestionId, NumberRange> numbers = e.numbering();
        Map<String, AdminExerciseView.GlossView> glosses = new LinkedHashMap<>();
        e.passage().ifPresent(p -> p.glosses().forEach(g -> {
            Map<String, AdminExerciseView.TextView> meanings = new TreeMap<>();
            g.meanings().asMap().forEach((lang, m) -> meanings.put(lang.value(), meaning(m)));
            glosses.put(g.key().value(), new AdminExerciseView.GlossView(g.surface(), g.lemma().orElse(null), meanings));
        }));
        List<AdminExerciseView.GroupView> groups = e.groups().stream().map(g -> group(g, numbers)).toList();
        return new AdminExerciseView(e.slug().value(), version, e.status().name(), e.revision().value(), e.skill().name(),
                e.targetLanguage().value(), e.title(), e.format().name(), e.level().map(Enum::name).orElse(null),
                e.timer().map(Timer::durationSeconds).orElse(null), e.timer().flatMap(Timer::lowTimeThreshold).orElse(null),
                e.placement().map(p -> p.stage().value()).orElse(null), e.placement().map(Placement::position).orElse(null),
                e.topics().stream().map(t -> t.value()).sorted().toList(), e.publishedAt().orElse(null),
                e.passage().map(p -> p.html().html()).orElse(null), e.passage().map(p -> p.wordCount()).orElse(null),
                glosses, groups, e.cover().map(m -> m.value()).orElse(null), coverUrl);
    }

    private static AdminExerciseView.GroupView group(QuestionGroup g, Map<QuestionId, NumberRange> numbers) {
        List<AdminExerciseView.QuestionView> questions = g.questions().stream().map(q -> question(q, numbers.get(q.id()))).toList();
        String heading = questions.isEmpty() ? null : heading(numbers.get(g.questions().getFirst().id()).first(),
                numbers.get(g.questions().getLast().id()).last());
        Map<String, String> translations = new TreeMap<>();
        g.instructionTranslations().asMap().forEach((lang, html) -> translations.put(lang.value(), html.html()));
        return new AdminExerciseView.GroupView(g.id().value(), g.type().code(), heading, g.instruction().html(), translations,
                g.chooseCount().orElse(null), g.wordLimit().map(WordLimit::maxWords).orElse(null),
                g.wordLimit().map(WordLimit::numbersAllowed).orElse(false), g.optionsReusable(),
                g.media().map(m -> m.value()).orElse(null), options(g.bank()), questions);
    }

    private static AdminExerciseView.QuestionView question(Question q, NumberRange range) {
        String text = q.stem() instanceof Stem.Text t ? t.text() : null;
        String pre = q.stem() instanceof Stem.Gap gap ? gap.pre() : null;
        String post = q.stem() instanceof Stem.Gap gap ? gap.post() : null;
        Map<String, AdminExerciseView.TextView> explanations = new TreeMap<>();
        q.explanations().asMap().forEach((lang, x) -> explanations.put(lang.value(), explanation(x)));
        return new AdminExerciseView.QuestionView(q.id().value(), range.first(), range.size() > 1 ? range.last() : null,
                text, pre, post, q.evidence().map(k -> k.value()).orElse(null), options(q.options()),
                q.answer().values(), explanations);
    }

    static String heading(int first, int last) {
        return first == last ? "Question " + first : "Questions " + first + "–" + last;
    }

    private static List<AdminExerciseView.OptionView> options(List<Option> options) {
        return options.stream().map(o -> new AdminExerciseView.OptionView(o.key().value(), o.label())).toList();
    }

    private static AdminExerciseView.TextView meaning(GlossMeaning m) {
        return new AdminExerciseView.TextView(m.text(), m.source().name(), m.reviewed());
    }

    private static AdminExerciseView.TextView explanation(Explanation x) {
        return new AdminExerciseView.TextView(x.html().html(), x.source().name(), x.reviewed());
    }
}
