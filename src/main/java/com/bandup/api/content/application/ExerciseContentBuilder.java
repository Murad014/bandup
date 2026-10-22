package com.bandup.api.content.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.bandup.api.content.application.port.HtmlSanitizer;
import com.bandup.api.content.application.port.MarkupInspector;
import com.bandup.api.content.application.port.QuestionTypes;
import com.bandup.api.content.application.port.ReferenceData;
import com.bandup.api.content.domain.exercise.aggregate.Exercise;
import com.bandup.api.content.domain.exercise.entity.Question;
import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.content.domain.exercise.repository.ExerciseRepository;
import com.bandup.api.content.domain.exercise.valueobject.AnswerKey;
import com.bandup.api.content.domain.exercise.valueobject.EvidenceKey;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseContent;
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
import com.bandup.api.content.domain.exercise.valueobject.StageId;
import com.bandup.api.content.domain.exercise.valueobject.Stem;
import com.bandup.api.content.domain.exercise.valueobject.TextSource;
import com.bandup.api.content.domain.exercise.valueobject.Timer;
import com.bandup.api.content.domain.exercise.valueobject.TopicId;
import com.bandup.api.content.domain.exercise.valueobject.WordLimit;
import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.LanguageFallbacks;
import com.bandup.api.shared.domain.language.Translations;

/**
 * Turns the editor's input into {@link ExerciseContent}: sanitizes every HTML
 * field, reads the passage's keys, checks references (question types,
 * languages, stages, topics, media, ids) and gives new groups and questions
 * their ids. Collects every problem with the JSON path of its field and
 * throws them together (422); the {@link Exercise} then checks its own rules.
 */
@Service
public class ExerciseContentBuilder {

    private final HtmlSanitizer sanitizer;
    private final MarkupInspector inspector;
    private final QuestionTypes questionTypes;
    private final ReferenceData references;
    private final LanguageCatalog languages;
    private final ExerciseRepository exercises;

    public ExerciseContentBuilder(HtmlSanitizer sanitizer, MarkupInspector inspector, QuestionTypes questionTypes,
            ReferenceData references, LanguageCatalog languages, ExerciseRepository exercises) {
        this.sanitizer = sanitizer;
        this.inspector = inspector;
        this.questionTypes = questionTypes;
        this.references = references;
        this.languages = languages;
        this.exercises = exercises;
    }

    /**
     * @param existing the exercise being edited (its group and question ids may be reused), or null when creating
     * @throws DomainException with every problem found
     */
    ExerciseContent build(ExerciseInput in, Exercise existing) {
        Build b = new Build(existing, languages.fallbacks(), questionTypes.all());
        Format format = in.format() == null ? Format.MINI
                : b.parse("format", () -> Format.valueOf(in.format()), "Use MINI or FULL.");
        Level level = in.level() == null ? null
                : b.parse("level", () -> Level.valueOf(in.level()), "Use a CEFR level: A1, A2, B1, B2, C1 or C2.");
        Timer timer = timer(in, b);
        Placement placement = placement(in, b);
        Set<TopicId> topics = new HashSet<>();
        for (Long topic : in.topicIds() == null ? List.<Long>of() : in.topicIds()) {
            if (topic == null || !references.topicExists(topic)) {
                b.add("topicIds", "Unknown topic " + topic + ".");
            } else {
                topics.add(new TopicId(topic));
            }
        }

        SanitizedHtml passage = null;
        MarkupKeys markup = MarkupKeys.none();
        if (in.passageHtml() != null && !in.passageHtml().isBlank()) {
            passage = sanitizer.passage(in.passageHtml());
            markup = inspector.inspect(passage);
        }
        Map<GlossKey, GlossNote> notes = new LinkedHashMap<>();
        (in.glosses() == null ? Map.<String, ExerciseInput.GlossInput>of() : in.glosses()).forEach((key, gloss) -> {
            if (GlossKey.isValid(key) && gloss != null) {
                notes.put(new GlossKey(key), new GlossNote(gloss.lemma(), b.translations("glosses." + key + ".meanings",
                        gloss.meanings(), t -> new GlossMeaning(t.text(), b.source(t), Boolean.TRUE.equals(t.reviewed())))));
            }
        });

        List<QuestionGroup> groups = new ArrayList<>();
        List<ExerciseInput.GroupInput> groupInputs = in.questionGroups() == null ? List.of() : in.questionGroups();
        for (int i = 0; i < groupInputs.size(); i++) {
            QuestionGroup group = group(groupInputs.get(i), "questionGroups[" + i + "]", b);
            if (group != null) {
                groups.add(group);
            }
        }
        b.throwIfAny();
        return new ExerciseContent(in.title(), format, level, timer, placement, topics, passage, markup, notes, groups);
    }

    private Timer timer(ExerciseInput in, Build b) {
        if (in.durationSeconds() == null) {
            if (in.lowTimeThresholdSeconds() != null) {
                b.add("durationSeconds", "Set the duration to use a low-time warning.");
            }
            return null;
        }
        return b.attempt("durationSeconds", () -> new Timer(in.durationSeconds(), in.lowTimeThresholdSeconds()), true);
    }

    private Placement placement(ExerciseInput in, Build b) {
        if (in.stageId() == null && in.position() == null) {
            return null;
        }
        if (in.stageId() == null || in.position() == null) {
            b.add(in.stageId() == null ? "stageId" : "position", "A stage needs a position, and a position needs a stage.");
            return null;
        }
        if (!references.stageExists(in.stageId())) {
            b.add("stageId", "Unknown stage " + in.stageId() + ".");
            return null;
        }
        return b.attempt("position", () -> new Placement(new StageId(in.stageId()), in.position()), false);
    }

    private QuestionGroup group(ExerciseInput.GroupInput in, String path, Build b) {
        if (in == null) {
            b.add(path, "A group can't be empty.");
            return null;
        }
        QuestionFamily family = in.type() == null ? null : b.types.get(in.type());
        if (family == null) {
            b.add(path + ".type", "Unknown question type '" + in.type() + "'.");
        }
        GroupId id = b.groupId(in.id(), path + ".id");
        WordLimit limit = in.maxWords() == null ? null
                : b.attempt(path + ".maxWords", () -> new WordLimit(in.maxWords(), Boolean.TRUE.equals(in.numbersAllowed())), false);
        MediaId media = null;
        if (in.mediaId() != null) {
            if (references.mediaExists(in.mediaId())) {
                media = new MediaId(in.mediaId());
            } else {
                b.add(path + ".mediaId", "Unknown picture " + in.mediaId() + ".");
            }
        }
        List<Option> bank = options(in.options(), path + ".options", b);
        Translations<SanitizedHtml> translations = b.translations(path + ".instructionTranslations",
                in.instructionTranslations() == null ? null : textInputs(in.instructionTranslations()),
                t -> sanitizer.text(t.text()));
        List<Question> questions = new ArrayList<>();
        List<ExerciseInput.QuestionInput> questionInputs = in.questions() == null ? List.of() : in.questions();
        for (int j = 0; j < questionInputs.size(); j++) {
            Question question = question(questionInputs.get(j), path + ".questions[" + j + "]", b);
            if (question != null) {
                questions.add(question);
            }
        }
        if (family == null || id == null) {
            return null;
        }
        return new QuestionGroup(id, new QuestionType(in.type(), family), sanitizer.text(in.instruction()), translations,
                in.chooseCount(), limit, Boolean.TRUE.equals(in.optionsReusable()), media, bank, questions);
    }

    private Question question(ExerciseInput.QuestionInput in, String path, Build b) {
        if (in == null) {
            b.add(path, "A question can't be empty.");
            return null;
        }
        QuestionId id = b.questionId(in.id(), path + ".id");
        Stem stem = in.pre() != null ? new Stem.Gap(in.pre(), in.post()) : new Stem.Text(in.text() == null ? "" : in.text());
        EvidenceKey evidence = null;
        if (in.evidenceId() != null && !in.evidenceId().isBlank()) {
            evidence = b.attempt(path + ".evidenceId", () -> new EvidenceKey(in.evidenceId().strip()), false);
        }
        List<Option> options = options(in.options(), path + ".options", b);
        AnswerKey answer = b.attempt(path + ".answer", () -> new AnswerKey(in.answer() == null ? List.of() : in.answer()), false);
        Translations<Explanation> explanations = b.translations(path + ".explanations", in.explanations(),
                t -> new Explanation(sanitizer.text(t.text()), b.source(t), Boolean.TRUE.equals(t.reviewed())));
        if (id == null || answer == null) {
            return null;
        }
        return new Question(id, stem, evidence, options, answer, explanations);
    }

    private static List<Option> options(List<ExerciseInput.OptionInput> inputs, String path, Build b) {
        List<Option> options = new ArrayList<>();
        List<ExerciseInput.OptionInput> list = inputs == null ? List.of() : inputs;
        for (int k = 0; k < list.size(); k++) {
            ExerciseInput.OptionInput in = list.get(k);
            Option option = in == null ? null
                    : b.attempt(path + "[" + k + "].key", () -> new Option(new OptionKey(in.key()), in.label()), false);
            if (option != null) {
                options.add(option);
            }
        }
        return options;
    }

    private static Map<String, ExerciseInput.TextInput> textInputs(Map<String, String> texts) {
        Map<String, ExerciseInput.TextInput> inputs = new LinkedHashMap<>();
        texts.forEach((lang, text) -> inputs.put(lang, new ExerciseInput.TextInput(text, null, null)));
        return inputs;
    }

    /** The state of one build: problems found so far, ids in use, reference data. */
    private final class Build {

        final List<Violation> violations = new ArrayList<>();
        final Map<String, QuestionFamily> types;
        final LanguageFallbacks fallbacks;
        final Set<GroupId> existingGroups = new HashSet<>();
        final Set<QuestionId> existingQuestions = new HashSet<>();
        final Set<GroupId> usedGroups = new HashSet<>();
        final Set<QuestionId> usedQuestions = new HashSet<>();

        Build(Exercise existing, LanguageFallbacks fallbacks, Map<String, QuestionFamily> types) {
            this.fallbacks = fallbacks;
            this.types = types;
            if (existing != null) {
                existing.groups().forEach(g -> {
                    existingGroups.add(g.id());
                    g.questions().forEach(q -> existingQuestions.add(q.id()));
                });
            }
        }

        void add(String field, String message) {
            violations.add(new Violation(field, message));
        }

        /** Runs a value object's constructor; its problems are reported at {@code field} (or at its own field). */
        <T> T attempt(String field, Supplier<T> create, boolean keepOwnFields) {
            try {
                return create.get();
            } catch (DomainException e) {
                e.violations().forEach(v -> add(keepOwnFields ? v.field() : field, v.message()));
                return null;
            }
        }

        <T> T parse(String field, Supplier<T> parse, String message) {
            try {
                return parse.get();
            } catch (IllegalArgumentException e) {
                add(field, message);
                return null;
            }
        }

        GroupId groupId(Long id, String field) {
            if (id == null) {
                return exercises.nextGroupId();
            }
            GroupId groupId = id > 0 ? new GroupId(id) : null;
            if (groupId == null || !existingGroups.contains(groupId)) {
                add(field, "Unknown group " + id + "; leave the id out for a new group.");
                return null;
            }
            if (!usedGroups.add(groupId)) {
                add(field, "Group " + id + " is listed twice.");
                return null;
            }
            return groupId;
        }

        QuestionId questionId(Long id, String field) {
            if (id == null) {
                return exercises.nextQuestionId();
            }
            QuestionId questionId = id > 0 ? new QuestionId(id) : null;
            if (questionId == null || !existingQuestions.contains(questionId)) {
                add(field, "Unknown question " + id + "; leave the id out for a new question.");
                return null;
            }
            if (!usedQuestions.add(questionId)) {
                add(field, "Question " + id + " is listed twice.");
                return null;
            }
            return questionId;
        }

        TextSource source(ExerciseInput.TextInput t) {
            if (t.source() == null) {
                return TextSource.HUMAN;
            }
            try {
                return TextSource.valueOf(t.source());
            } catch (IllegalArgumentException e) {
                return TextSource.HUMAN;
            }
        }

        <T> Translations<T> translations(String path, Map<String, ExerciseInput.TextInput> inputs,
                Function<ExerciseInput.TextInput, T> create) {
            Map<LanguageCode, T> values = new LinkedHashMap<>();
            if (inputs != null) {
                inputs.forEach((lang, input) -> {
                    String field = path + "." + lang;
                    LanguageCode code = parse(field, () -> LanguageCode.of(lang), "Unknown language '" + lang + "'.");
                    if (code == null) {
                        return;
                    }
                    if (!fallbacks.isKnown(code)) {
                        add(field, "Unknown language '" + lang + "'.");
                    } else if (input == null || input.text() == null || input.text().isBlank()) {
                        add(field, "The text can't be empty; leave the language out instead.");
                    } else {
                        T value = attempt(field, () -> create.apply(input), false);
                        if (value != null) {
                            values.put(fallbacks.storedSpelling(code).orElse(code), value);
                        }
                    }
                });
            }
            return Translations.of(values);
        }

        void throwIfAny() {
            if (!violations.isEmpty()) {
                throw new DomainException(violations);
            }
        }
    }
}
