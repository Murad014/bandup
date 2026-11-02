package com.bandup.api.grammar.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.bandup.api.grammar.application.port.TextSanitizer;
import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LearnCard;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonText;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;
import com.bandup.api.grammar.domain.lesson.valueobject.Topic;
import com.bandup.api.grammar.domain.lesson.valueobject.WorkedExample;
import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.LanguageFallbacks;
import com.bandup.api.shared.domain.language.Translations;

/**
 * Turns a {@link LessonInput} into a {@link LessonBody} (grammar design §4.2).
 * Texts are sanitized and their languages checked. A missing value becomes an
 * empty one, so the {@code LessonPolicy} reports all of them in one go; only
 * shapes that can't be read (an unknown level or item type, a text that is
 * neither a string nor an object, a wrong kind of answer) are reported here,
 * all together, before the policy runs.
 */
@Service
@RequiredArgsConstructor
public class LessonInputBuilder {

    private final TextSanitizer sanitizer;
    private final LanguageCatalog languages;

    /** @throws DomainException with every shape problem found */
    LessonBody build(LessonInput in) {
        Build b = new Build(languages.fallbacks());
        LessonBody body = b.body(in);
        if (!b.violations.isEmpty()) {
            throw new DomainException(b.violations);
        }
        return body;
    }

    /** One build: collects violations as it goes. */
    @RequiredArgsConstructor
    private final class Build {

        private final LanguageFallbacks fallbacks;
        private final List<Violation> violations = new ArrayList<>();

        LessonBody body(LessonInput in) {
            GrammarLevel level = level(in.level());
            LessonInput.TopicInput topic = in.topic() == null ? new LessonInput.TopicInput(null, null) : in.topic();
            LessonInput.WorkedInput worked = in.worked() == null ? new LessonInput.WorkedInput(null, null, null)
                    : in.worked();
            List<LearnCard> learn = new ArrayList<>();
            List<LessonInput.LearnCardInput> cards = list(in.learn());
            for (int i = 0; i < cards.size(); i++) {
                learn.add(card(cards.get(i), "learn[" + i + "]"));
            }
            return new LessonBody(level, new Topic(plain(topic.key()), text(topic.label(), "topic.label")),
                    text(in.title(), "title"), text(in.goal(), "goal"),
                    in.minutes() == null ? LessonBody.DEFAULT_MINUTES : in.minutes(), position(in.position()), learn,
                    new WorkedExample(text(worked.question(), "worked.question"), texts(worked.steps(), "worked.steps"),
                            plain(worked.answer())),
                    items(in.practice(), "practice"), items(in.check(), "check"));
        }

        private GrammarLevel level(String code) {
            try {
                return GrammarLevel.valueOf(Objects.requireNonNull(code));
            } catch (RuntimeException e) {
                violations.add(new Violation("level", "Use A2, B1, B2 or C1."));
                return GrammarLevel.A2; // placeholder; the violation stops the save
            }
        }

        private Position position(Integer position) {
            if (position == null) {
                return null;
            }
            try {
                return new Position(position);
            } catch (DomainException e) {
                violations.addAll(e.violations());
                return null;
            }
        }

        private LearnCard card(LessonInput.LearnCardInput in, String field) {
            if (in == null) {
                violations.add(new Violation(field, "Give the learn card."));
                return new LearnCard("", empty(), empty(), null, List.of(), List.of(), null);
            }
            List<LearnCard.Example> examples = new ArrayList<>();
            List<LessonInput.ExampleInput> given = list(in.examples());
            for (int i = 0; i < given.size(); i++) {
                String exampleField = field + ".examples[" + i + "]";
                LessonInput.ExampleInput example = given.get(i);
                if (example == null || example.ok() == null) {
                    violations.add(new Violation(exampleField + ".ok", "Say if the example is right (true) or wrong (false)."));
                }
                examples.add(new LearnCard.Example(example != null && Boolean.TRUE.equals(example.ok()),
                        text(example == null ? null : example.text(), exampleField + ".text")));
            }
            return new LearnCard(plain(in.icon()), text(in.label(), field + ".label"), text(in.heading(), field + ".heading"),
                    optionalText(in.body(), field + ".body"), texts(in.steps(), field + ".steps"), examples,
                    optionalText(in.tip(), field + ".tip"));
        }

        private List<Item> items(List<LessonInput.ItemInput> in, String list) {
            List<Item> items = new ArrayList<>();
            List<LessonInput.ItemInput> given = list(in);
            for (int i = 0; i < given.size(); i++) {
                item(given.get(i), list + "[" + i + "]").ifPresent(items::add);
            }
            return items;
        }

        private Optional<Item> item(LessonInput.ItemInput in, String field) {
            if (in == null) {
                violations.add(new Violation(field, "Give the item."));
                return Optional.empty();
            }
            ItemId id = new ItemId(plain(in.id()));
            LessonText prompt = text(in.prompt(), field + ".prompt");
            LessonText explanation = text(in.explanation(), field + ".explanation");
            String type = in.type() == null ? "" : in.type();
            return switch (type) {
                case "choose" -> Optional.of(new Item.Choose(id, prompt, strings(in.options()), index(in.answer(), field),
                        texts(in.whys(), field + ".whys"), explanation));
                case "type" -> Optional.of(new Item.Type(id, prompt, plain(in.sentence()), strings(in.accept()), in.show(),
                        explanation));
                case "fix" -> Optional.of(new Item.Fix(id, prompt, plain(in.wrong()), strings(in.accept()), in.show(),
                        explanation));
                case "build" -> Optional.of(new Item.Build(id, prompt, strings(in.tiles()), sentence(in.answer(), field),
                        explanation));
                default -> {
                    violations.add(new Violation(field + ".type", "Use choose, type, fix or build."));
                    yield Optional.empty();
                }
            };
        }

        private int index(Object answer, String field) {
            if (answer == null) {
                return -1; // the policy asks for it
            }
            if (answer instanceof Integer index) {
                return index;
            }
            violations.add(new Violation(field + ".answer", "Give the index of the right option (0 = first)."));
            return -1;
        }

        private String sentence(Object answer, String field) {
            if (answer == null || answer instanceof String) {
                return plain((String) answer);
            }
            violations.add(new Violation(field + ".answer", "Give the answer sentence as text."));
            return "";
        }

        /** A text: a string (English) or an object of languages. Null becomes an empty English text. */
        private LessonText text(Object value, String field) {
            if (value == null) {
                return empty();
            }
            if (value instanceof String english) {
                return LessonText.english(sanitizer.clean(english));
            }
            if (value instanceof Map<?, ?> byLanguage) {
                Map<LanguageCode, String> values = new LinkedHashMap<>();
                byLanguage.forEach((key, text) -> {
                    Optional<LanguageCode> language = language(String.valueOf(key), field);
                    if (!(text instanceof String s)) {
                        violations.add(new Violation(field + "." + key, "Give the text as a string."));
                    } else {
                        language.ifPresent(l -> values.put(l, sanitizer.clean(s)));
                    }
                });
                return new LessonText(Translations.of(values));
            }
            violations.add(new Violation(field, "Give a text, or an object of languages like {\"en\": \"…\"}."));
            return empty();
        }

        private Optional<LanguageCode> language(String code, String field) {
            try {
                LanguageCode language = LanguageCode.of(code);
                if (fallbacks.isKnown(language)) {
                    return fallbacks.storedSpelling(language);
                }
            } catch (RuntimeException e) {
                // reported below
            }
            violations.add(new Violation(field + "." + code, "Unknown language \"" + code + "\"."));
            return Optional.empty();
        }

        private LessonText optionalText(Object value, String field) {
            return value == null ? null : text(value, field);
        }

        private List<LessonText> texts(List<Object> values, String field) {
            List<LessonText> texts = new ArrayList<>();
            List<Object> given = list(values);
            for (int i = 0; i < given.size(); i++) {
                texts.add(text(given.get(i), field + "[" + i + "]"));
            }
            return texts;
        }

        private static LessonText empty() {
            return LessonText.english("");
        }

        private static String plain(String value) {
            return value == null ? "" : value.strip();
        }

        private static List<String> strings(List<String> values) {
            return list(values).stream().map(Build::plain).toList();
        }

        private static <T> List<T> list(List<T> values) {
            return values == null ? List.of() : values;
        }
    }
}
