package com.bandup.api.grammar.domain.lesson.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.grammar.domain.lesson.valueobject.LearnCard;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonText;
import com.bandup.api.grammar.domain.lesson.valueobject.WorkedExample;
import com.bandup.api.shared.domain.ddd.DomainService;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;

/**
 * The rules of the lesson format ({@code lesson-format.md}; grammar
 * requirement 9.2). Applied on every save, drafts included, because an AI
 * pipeline needs every problem at once to retry. Each violation names the
 * JSON path of its field ({@code check[3].answer}).
 */
@DomainService
@RequiredArgsConstructor
public final class LessonPolicy {

    private static final Pattern TOPIC_KEY = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern GAP = Pattern.compile("___");

    private final AnswerNormaliser normaliser;

    public List<Violation> check(LessonBody body) {
        List<Violation> violations = new ArrayList<>();
        String key = body.topic().key();
        if (key.length() < 2 || key.length() > 60 || !TOPIC_KEY.matcher(key).matches()) {
            violations.add(new Violation("topic.key", "Use 2 to 60 lower-case letters, digits and single hyphens."));
        }
        text(body.topic().label(), "topic.label", 80, violations);
        text(body.title(), "title", 120, violations);
        text(body.goal(), "goal", 300, violations);
        if (body.minutes() < 5 || body.minutes() > 40) {
            violations.add(new Violation("minutes", "A lesson takes 5 to 40 minutes."));
        }
        count(body.learn().size(), 3, 8, "learn", "learn cards", violations);
        for (int i = 0; i < body.learn().size(); i++) {
            learnCard(body.learn().get(i), "learn[" + i + "]", violations);
        }
        worked(body.worked(), violations);
        count(body.practice().size(), 2, 6, "practice", "practice items", violations);
        count(body.check().size(), 5, 15, "check", "check items", violations);
        Set<String> ids = new HashSet<>();
        items(body.practice(), "practice", "p", ids, violations);
        items(body.check(), "check", "c", ids, violations);
        if (body.check().size() >= 2 && body.check().stream().map(Item::code).distinct().count() < 2) {
            violations.add(new Violation("check", "Use at least two different item types in the check."));
        }
        return violations;
    }

    private void learnCard(LearnCard card, String field, List<Violation> violations) {
        if (card.icon().isBlank() || card.icon().length() > 8) {
            violations.add(new Violation(field + ".icon", "Give one emoji as the icon."));
        }
        text(card.label(), field + ".label", 80, violations);
        text(card.heading(), field + ".heading", 160, violations);
        card.bodyText().ifPresent(t -> text(t, field + ".body", 1000, violations));
        card.tipText().ifPresent(t -> text(t, field + ".tip", 300, violations));
        if (card.bodyText().isEmpty() && card.steps().isEmpty() && card.examples().isEmpty()) {
            violations.add(new Violation(field, "A learn card needs a body, steps or examples."));
        }
        if (card.steps().size() > 5) {
            violations.add(new Violation(field + ".steps", "Use at most 5 steps."));
        }
        if (card.examples().size() > 4) {
            violations.add(new Violation(field + ".examples", "Use at most 4 examples."));
        }
        for (int i = 0; i < card.steps().size(); i++) {
            text(card.steps().get(i), field + ".steps[" + i + "]", 300, violations);
        }
        for (int i = 0; i < card.examples().size(); i++) {
            text(card.examples().get(i).text(), field + ".examples[" + i + "].text", 300, violations);
        }
    }

    private void worked(WorkedExample worked, List<Violation> violations) {
        text(worked.question(), "worked.question", 300, violations);
        count(worked.steps().size(), 2, 5, "worked.steps", "steps", violations);
        for (int i = 0; i < worked.steps().size(); i++) {
            text(worked.steps().get(i), "worked.steps[" + i + "]", 300, violations);
        }
        if (worked.answer().isBlank()) {
            violations.add(new Violation("worked.answer", "Give the worked example's answer."));
        }
    }

    private void items(List<Item> items, String list, String prefix, Set<String> ids, List<Violation> violations) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            String field = list + "[" + i + "]";
            String id = item.id().value();
            if (!id.matches(prefix + "[1-9][0-9]{0,2}")) {
                violations.add(new Violation(field + ".id", "Use ids like " + prefix + "1, " + prefix + "2, …"));
            } else if (!ids.add(id)) {
                violations.add(new Violation(field + ".id", "The id " + id + " is used twice."));
            }
            text(item.prompt(), field + ".prompt", 300, violations);
            text(item.explanation(), field + ".explanation", 600, violations);
            switch (item) {
                case Item.Choose choose -> choose(choose, field, violations);
                case Item.Type type -> typed(type, field, violations);
                case Item.Fix fix -> fix(fix, field, violations);
                case Item.Build build -> build(build, field, violations);
            }
        }
    }

    private void choose(Item.Choose item, String field, List<Violation> violations) {
        List<String> options = item.options();
        count(options.size(), 2, 4, field + ".options", "options", violations);
        if (options.stream().anyMatch(String::isBlank)) {
            violations.add(new Violation(field + ".options", "An option is empty."));
        }
        if (options.stream().map(o -> o.strip().toLowerCase(Locale.ROOT)).distinct().count() != options.size()) {
            violations.add(new Violation(field + ".options", "Two options are the same."));
        }
        if (item.answer() < 0 || item.answer() >= options.size()) {
            violations.add(new Violation(field + ".answer", "The answer must be the index of one option (0 = first)."));
        }
        if (!item.whys().isEmpty() && item.whys().size() != options.size()) {
            violations.add(new Violation(field + ".whys", "Give one why per option, or none."));
        }
        for (int i = 0; i < item.whys().size(); i++) {
            text(item.whys().get(i), field + ".whys[" + i + "]", 300, violations);
        }
    }

    private void typed(Item.Type item, String field, List<Violation> violations) {
        if (GAP.matcher(item.sentence()).results().count() != 1) {
            violations.add(new Violation(field + ".sentence", "The sentence needs exactly one ___ gap."));
        }
        accepted(item.accept(), item.show(), field, 4, violations);
    }

    private void fix(Item.Fix item, String field, List<Violation> violations) {
        if (item.wrong().isBlank()) {
            violations.add(new Violation(field + ".wrong", "Give the wrong sentence."));
        }
        accepted(item.accept(), item.show(), field, Integer.MAX_VALUE, violations);
        String wrong = normaliser.normalise(item.wrong());
        if (!wrong.isEmpty() && item.accept().stream().map(normaliser::normalise).anyMatch(wrong::equals)) {
            violations.add(new Violation(field + ".accept", "The wrong sentence can't be an accepted answer."));
        }
    }

    private void accepted(List<String> accept, String show, String field, int maxWords, List<Violation> violations) {
        count(accept.size(), 1, 5, field + ".accept", "accepted answers", violations);
        List<String> normalised = accept.stream().map(normaliser::normalise).toList();
        if (normalised.contains("")) {
            violations.add(new Violation(field + ".accept", "An accepted answer is empty."));
        }
        if (new HashSet<>(normalised).size() != normalised.size()) {
            violations.add(new Violation(field + ".accept", "Two accepted answers are the same."));
        }
        if (normalised.stream().anyMatch(a -> a.split(" ").length > maxWords)) {
            violations.add(new Violation(field + ".accept", "An accepted answer has more than " + maxWords + " words."));
        }
        if (show != null && show.isBlank()) {
            violations.add(new Violation(field + ".show", "Leave show out or give the answer to show."));
        }
    }

    private void build(Item.Build item, String field, List<Violation> violations) {
        count(item.tiles().size(), 2, 12, field + ".tiles", "tiles", violations);
        if (item.tiles().stream().anyMatch(String::isBlank)) {
            violations.add(new Violation(field + ".tiles", "A tile is empty."));
        }
        Map<String, Long> tiles = words(String.join(" ", item.tiles()));
        if (!tiles.equals(words(item.answer()))) {
            violations.add(new Violation(field + ".answer", "The answer must use exactly the tiles' words."));
        } else if (normaliser.normalise(String.join(" ", item.tiles())).equals(normaliser.normalise(item.answer()))) {
            violations.add(new Violation(field + ".tiles", "The tiles are already in answer order; mix them."));
        }
    }

    private Map<String, Long> words(String sentence) {
        String normalised = normaliser.normalise(sentence);
        return normalised.isEmpty() ? Map.of()
                : Arrays.stream(normalised.split(" ")).collect(Collectors.groupingBy(w -> w, Collectors.counting()));
    }

    private static void text(LessonText text, String field, int maxLength, List<Violation> violations) {
        if (text.inEnglish().isBlank()) {
            violations.add(new Violation(field, "Write the English text (en)."));
        }
        text.values().asMap().forEach((language, value) -> {
            if (value.isBlank()) {
                if (!language.equals(LanguageCode.ENGLISH)) { // a blank English text is reported above
                    violations.add(new Violation(field + "." + language, "This text is empty."));
                }
            } else if (value.length() > maxLength) {
                violations.add(new Violation(field + (language.equals(LanguageCode.ENGLISH) ? "" : "." + language),
                        "Use at most " + maxLength + " characters."));
            }
        });
    }

    private static void count(int size, int min, int max, String field, String what, List<Violation> violations) {
        if (size < min || size > max) {
            violations.add(new Violation(field, "Use " + min + " to " + max + " " + what + "."));
        }
    }
}
