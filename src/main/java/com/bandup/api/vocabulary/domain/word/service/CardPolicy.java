package com.bandup.api.vocabulary.domain.word.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;

import com.bandup.api.shared.domain.ddd.DomainService;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;
import com.bandup.api.vocabulary.domain.word.valueobject.CefrLevel;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

/**
 * The rules of a word card (card-format.md). Applied to every AI answer and
 * every admin edit; reports every problem at once with its JSON path, so the
 * AI can be retried with the list.
 */
@DomainService
@RequiredArgsConstructor
public final class CardPolicy {

    public static final Set<String> PARTS_OF_SPEECH = Set.of("noun", "verb", "adjective", "adverb", "phrasal verb",
            "phrase", "preposition", "conjunction", "other");
    private static final Pattern GAP = Pattern.compile("___");

    private final WordNormaliser normaliser;

    public List<Violation> check(Lemma lemma, Collection<WordForm> forms, CardContent c) {
        List<Violation> v = new ArrayList<>();
        if (!WordGate.WORDS.matcher(lemma.value()).matches()) {
            v.add(new Violation("lemma", "Use 1 to 3 lower-case English words."));
        }
        if (forms.isEmpty() || forms.size() > 12) {
            v.add(new Violation("forms", "Give 1 to 12 forms."));
        }
        if (forms.stream().anyMatch(f -> !WordGate.WORDS.matcher(f.value()).matches())) {
            v.add(new Violation("forms", "Every form is 1 to 3 lower-case English words."));
        }
        if (!forms.contains(lemma.asForm())) {
            v.add(new Violation("forms", "The forms must include the lemma."));
        }
        if (!partOfSpeech(c.pos())) {
            v.add(new Violation("pos", "Use noun, verb, adjective, adverb, phrasal verb, phrase, preposition, conjunction or other."));
        }
        if (!CefrLevel.isLevel(c.level())) {
            v.add(new Violation("level", "Use A1, A2, B1, B2, C1 or C2."));
        }
        if (c.ipa().length() > 60 || c.ipa().length() < 3 || !c.ipa().startsWith("/") || !c.ipa().endsWith("/")) {
            v.add(new Violation("ipa", "Write the IPA between slashes, at most 60 characters."));
        }
        text(c.meaning().en(), "meaning.en", 3, 160, v);
        if (containsWord(c.meaning().en(), List.of(lemma.asForm()))) {
            v.add(new Violation("meaning.en", "Explain the word without using it."));
        }
        text(c.meaning().az(), "meaning.az", 1, 120, v);
        if (c.examples().size() != 2) {
            v.add(new Violation("examples", "Give 2 examples."));
        }
        for (int i = 0; i < c.examples().size(); i++) {
            String e = c.examples().get(i);
            text(e, "examples[" + i + "]", 20, 160, v);
            if (!containsWord(e, forms)) {
                v.add(new Violation("examples[" + i + "]", "The example must use the word."));
            }
        }
        if (c.collocations().isEmpty() || c.collocations().size() > 4) {
            v.add(new Violation("collocations", "Give 1 to 4 word pairs."));
        }
        for (int i = 0; i < c.collocations().size(); i++) {
            String col = c.collocations().get(i);
            text(col, "collocations[" + i + "]", 1, 40, v);
            if (!containsWord(col, forms)) {
                v.add(new Violation("collocations[" + i + "]", "The word pair must use the word."));
            }
        }
        if (c.family().size() > 4) {
            v.add(new Violation("family", "Give at most 4 family words."));
        }
        for (int i = 0; i < c.family().size(); i++) {
            text(c.family().get(i), "family[" + i + "]", 1, 40, v);
        }
        gapSentence(c.gap().sentence(), "gap.sentence", v);
        if (!isForm(c.gap().answer(), forms)) {
            v.add(new Violation("gap.answer", "The answer must be one of the forms."));
        }
        gapSentence(c.pair().sentence(), "pair.sentence", v);
        List<String> options = c.pair().options();
        if (options.size() != 4) {
            v.add(new Violation("pair.options", "Give 4 options."));
        }
        if (options.stream().anyMatch(o -> o == null || o.isBlank() || o.length() > 40 || html(o))) {
            v.add(new Violation("pair.options", "Every option is plain text of 1 to 40 characters."));
        }
        if (options.stream().map(normaliser::normalise).distinct().count() != options.size()) {
            v.add(new Violation("pair.options", "Two options are the same."));
        }
        if (c.pair().answer() < 0 || c.pair().answer() >= options.size()) {
            v.add(new Violation("pair.answer", "The answer must be the index of one option (0 = first)."));
        } else if (!isForm(options.get(c.pair().answer()), forms)) {
            v.add(new Violation("pair.answer", "The right option must be one of the forms."));
        }
        if (c.wrongMeanings().size() != 3) {
            v.add(new Violation("wrongMeanings", "Give 3 wrong meanings."));
        }
        for (int i = 0; i < c.wrongMeanings().size(); i++) {
            text(c.wrongMeanings().get(i), "wrongMeanings[" + i + "]", 3, 160, v);
        }
        Set<String> meanings = new HashSet<>();
        meanings.add(normaliser.normalise(c.meaning().en()));
        if (c.wrongMeanings().stream().map(normaliser::normalise).anyMatch(m -> !meanings.add(m))) {
            v.add(new Violation("wrongMeanings", "The wrong meanings must differ from each other and from the meaning."));
        }
        return v;
    }

    /** Whether {@code text} contains one of the forms as whole words (case ignored). */
    public boolean containsWord(String text, Collection<WordForm> forms) {
        String lower = " " + normaliser.normalise(text).replaceAll("[^a-z' -]", " ") + " ";
        return forms.stream().anyMatch(f -> lower.contains(" " + f.value() + " "));
    }

    private boolean isForm(String text, Collection<WordForm> forms) {
        String normalised = normaliser.normalise(text);
        return forms.stream().anyMatch(f -> f.value().equals(normalised));
    }

    private static boolean partOfSpeech(String pos) {
        String[] parts = pos.toLowerCase(Locale.ROOT).split(" · ");
        if (parts.length > 2) {
            return false;
        }
        for (String part : parts) {
            if (!PARTS_OF_SPEECH.contains(part.strip())) {
                return false;
            }
        }
        return true;
    }

    private static void gapSentence(String sentence, String field, List<Violation> v) {
        text(sentence, field, 20, 160, v);
        if (GAP.matcher(sentence).results().count() != 1) {
            v.add(new Violation(field, "The sentence needs exactly one ___ gap."));
        }
    }

    private static void text(String value, String field, int min, int max, List<Violation> v) {
        if (value == null || value.isBlank()) {
            v.add(new Violation(field, "Write it."));
        } else if (value.strip().length() < min || value.length() > max) {
            v.add(new Violation(field, "Use " + min + " to " + max + " characters."));
        } else if (html(value)) {
            v.add(new Violation(field, "Plain text only, no HTML."));
        }
    }

    private static boolean html(String value) {
        return value.indexOf('<') >= 0 || value.indexOf('>') >= 0;
    }
}
