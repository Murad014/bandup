package com.bandup.api.content.domain.exercise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bandup.api.content.domain.exercise.aggregate.Exercise;
import com.bandup.api.content.domain.exercise.entity.Question;
import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.content.domain.exercise.valueobject.AnswerKey;
import com.bandup.api.content.domain.exercise.valueobject.EvidenceKey;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseContent;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseId;
import com.bandup.api.content.domain.exercise.valueobject.Explanation;
import com.bandup.api.content.domain.exercise.valueobject.Format;
import com.bandup.api.content.domain.exercise.valueobject.GlossKey;
import com.bandup.api.content.domain.exercise.valueobject.GlossMeaning;
import com.bandup.api.content.domain.exercise.valueobject.GlossNote;
import com.bandup.api.content.domain.exercise.valueobject.GroupId;
import com.bandup.api.content.domain.exercise.valueobject.Level;
import com.bandup.api.content.domain.exercise.valueobject.MarkupKeys;
import com.bandup.api.content.domain.exercise.valueobject.MediaId;
import com.bandup.api.content.domain.exercise.valueobject.Option;
import com.bandup.api.content.domain.exercise.valueobject.OptionKey;
import com.bandup.api.content.domain.exercise.valueobject.Placement;
import com.bandup.api.content.domain.exercise.valueobject.QuestionFamily;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.QuestionType;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;
import com.bandup.api.content.domain.exercise.valueobject.Skill;
import com.bandup.api.content.domain.exercise.valueobject.Slug;
import com.bandup.api.content.domain.exercise.valueobject.StageId;
import com.bandup.api.content.domain.exercise.valueobject.Stem;
import com.bandup.api.content.domain.exercise.valueobject.TextSource;
import com.bandup.api.content.domain.exercise.valueobject.Timer;
import com.bandup.api.content.domain.exercise.valueobject.TopicId;
import com.bandup.api.content.domain.exercise.valueobject.WordLimit;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

/**
 * A small, publishable reading exercise modelled on the seeded Wolves passage:
 * group 10 = TFNG Q1–2 (ids 101, 102), group 20 = FILL Q3 (id 201, two words
 * max), with glosses g1 (eliminated) and g7 (willows) and evidence e1, e4.
 */
public final class ExerciseFixtures {

    public static final String HTML = "<span class=\"ev\" id=\"e1\">Wolves were <span class=\"w\" data-g=\"g1\">eliminated</span> "
            + "by hunting.</span> <span class=\"ev\" id=\"e4\">Elk ate the <span class=\"w\" data-g=\"g7\">willows</span> "
            + "along the riverbanks.</span>";
    public static final MarkupKeys MARKUP = new MarkupKeys(
            List.of(new MarkupKeys.GlossMark("g1", "eliminated"), new MarkupKeys.GlossMark("g7", "willows")),
            List.of("e1", "e4"));
    public static final LanguageCode AZ = LanguageCode.of("az");

    private ExerciseFixtures() {
    }

    public static Exercise wolves() {
        return Exercise.draft(new ExerciseId(1), new Slug("wolves"), Skill.READING, LanguageCode.ENGLISH, content().build());
    }

    public static Builder content() {
        return new Builder();
    }

    public static QuestionType type(String code, QuestionFamily family) {
        return new QuestionType(code, family);
    }

    public static QuestionGroup tfng() {
        return group(10, type("TFNG", QuestionFamily.CHOICE_FIXED), null, null, List.of(),
                question(101, new Stem.Text("Wolves died of disease."), "e1", AnswerKey.of("FALSE")),
                question(102, new Stem.Text("Elk ate willows."), "e4", AnswerKey.of("TRUE")));
    }

    public static QuestionGroup fill() {
        return group(20, type("FILL", QuestionFamily.GAP), null, new WordLimit(2, false), List.of(),
                question(201, new Stem.Gap("Elk ate trees along the", "."), "e4", AnswerKey.of("riverbanks", "river banks")));
    }

    public static QuestionGroup group(long id, QuestionType type, Integer chooseCount, WordLimit limit, List<Option> bank,
            Question... questions) {
        return new QuestionGroup(new GroupId(id), type, new SanitizedHtml("Answer the questions."), Translations.empty(),
                chooseCount, limit, false, null, bank, List.of(questions));
    }

    public static QuestionGroup group(long id, QuestionType type, Integer chooseCount, boolean reusable, MediaId media,
            List<Option> bank, Question... questions) {
        return new QuestionGroup(new GroupId(id), type, new SanitizedHtml("Answer the questions."), Translations.empty(),
                chooseCount, null, reusable, media, bank, List.of(questions));
    }

    public static Question question(long id, Stem stem, String evidence, AnswerKey answer, Option... options) {
        return new Question(new QuestionId(id), stem, evidence == null ? null : new EvidenceKey(evidence), List.of(options),
                answer, Translations.<Explanation>empty().with(LanguageCode.ENGLISH,
                        new Explanation(new SanitizedHtml("Because the text says so."), TextSource.HUMAN, true)));
    }

    public static Option option(String key, String label) {
        return new Option(new OptionKey(key), label);
    }

    public static List<Option> options(String... keys) {
        List<Option> options = new ArrayList<>();
        for (String key : keys) {
            options.add(option(key, "Option " + key));
        }
        return options;
    }

    /** Builds {@link ExerciseContent}; starts as the publishable Wolves content. */
    public static final class Builder {

        private String title = "The Return of the Wolves";
        private Timer timer = new Timer(300, 60);
        private Placement placement = new Placement(new StageId(1), 4);
        private SanitizedHtml html = new SanitizedHtml(HTML);
        private MarkupKeys markup = MARKUP;
        private Map<GlossKey, GlossNote> notes = new LinkedHashMap<>(Map.of(
                new GlossKey("g1"), new GlossNote("eliminate", Translations.<GlossMeaning>empty()
                        .with(AZ, new GlossMeaning("məhv edilmək", TextSource.HUMAN, true))),
                new GlossKey("g9"), new GlossNote("forgotten", Translations.empty())));
        private List<QuestionGroup> groups = List.of(tfng(), fill());

        public Builder title(String value) {
            title = value;
            return this;
        }

        public Builder passage(String value, MarkupKeys keys) {
            html = value == null ? null : new SanitizedHtml(value);
            markup = keys;
            return this;
        }

        public Builder groups(QuestionGroup... value) {
            groups = List.of(value);
            return this;
        }

        public Builder noTimer() {
            timer = null;
            return this;
        }

        public ExerciseContent build() {
            return new ExerciseContent(title, Format.MINI, Level.B1, timer, placement, Set.of(new TopicId(3)), html, markup,
                    notes, groups);
        }
    }
}
