package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.bandup.api.grammar.domain.lesson.service.AnswerNormaliser;
import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.language.LanguageChain;

/**
 * One question of a lesson (grammar requirement 5). Four types: {@link Choose},
 * {@link Type}, {@link Fix} and {@link Build}. Each grades its own answers; the
 * content rules live in the {@code LessonPolicy}.
 */
@ValueObject
public sealed interface Item permits Item.Choose, Item.Type, Item.Fix, Item.Build {

    ItemId id();

    LessonText prompt();

    LessonText explanation();

    /** The type code of the lesson format: {@code choose}, {@code type}, {@code fix}, {@code build}. */
    String code();

    /**
     * Grades one answer.
     *
     * @throws DomainException (field {@code answer}) when the answer doesn't fit the type
     */
    ItemFeedback grade(GivenAnswer answer, AnswerNormaliser normaliser, LanguageChain language);

    /** Pick one of 2–4 options; {@code whys} (one per option, or none) say why each option is right or wrong. */
    record Choose(ItemId id, LessonText prompt, List<String> options, int answer, List<LessonText> whys,
            LessonText explanation) implements Item {

        public Choose {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(prompt, "prompt");
            options = List.copyOf(options);
            whys = List.copyOf(whys);
            Objects.requireNonNull(explanation, "explanation");
        }

        @Override
        public String code() {
            return "choose";
        }

        @Override
        public ItemFeedback grade(GivenAnswer given, AnswerNormaliser normaliser, LanguageChain language) {
            if (!(given instanceof GivenAnswer.Choice choice) || choice.index() < 0 || choice.index() >= options.size()) {
                throw DomainException.of("answer", "Pick one of the options.");
            }
            boolean correct = choice.index() == answer;
            String why = !correct && !whys.isEmpty() ? whys.get(choice.index()).in(language) : null;
            return new ItemFeedback(correct, options.get(answer), why, explanation.in(language));
        }
    }

    /** Type the missing words of {@code sentence} (one {@code ___} gap). */
    record Type(ItemId id, LessonText prompt, String sentence, List<String> accept, String show,
            LessonText explanation) implements Item {

        public Type {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(sentence, "sentence");
            accept = List.copyOf(accept);
            Objects.requireNonNull(explanation, "explanation");
        }

        @Override
        public String code() {
            return "type";
        }

        @Override
        public ItemFeedback grade(GivenAnswer given, AnswerNormaliser normaliser, LanguageChain language) {
            return gradeText(given, accept, show, explanation, normaliser, language);
        }
    }

    /** Correct the {@code wrong} sentence by typing the right words. */
    record Fix(ItemId id, LessonText prompt, String wrong, List<String> accept, String show,
            LessonText explanation) implements Item {

        public Fix {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(wrong, "wrong");
            accept = List.copyOf(accept);
            Objects.requireNonNull(explanation, "explanation");
        }

        @Override
        public String code() {
            return "fix";
        }

        @Override
        public ItemFeedback grade(GivenAnswer given, AnswerNormaliser normaliser, LanguageChain language) {
            return gradeText(given, accept, show, explanation, normaliser, language);
        }
    }

    /** Put the word {@code tiles} in order to make {@code answer}. */
    record Build(ItemId id, LessonText prompt, List<String> tiles, String answer, LessonText explanation)
            implements Item {

        public Build {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(prompt, "prompt");
            tiles = List.copyOf(tiles);
            Objects.requireNonNull(answer, "answer");
            Objects.requireNonNull(explanation, "explanation");
        }

        @Override
        public String code() {
            return "build";
        }

        @Override
        public ItemFeedback grade(GivenAnswer given, AnswerNormaliser normaliser, LanguageChain language) {
            if (!(given instanceof GivenAnswer.Tiles placed) || !sameTiles(placed.words(), tiles)) {
                throw DomainException.of("answer", "Use every tile exactly once.");
            }
            boolean correct = normaliser.normalise(String.join(" ", placed.words())).equals(normaliser.normalise(answer));
            return new ItemFeedback(correct, answer, null, explanation.in(language));
        }

        private static boolean sameTiles(List<String> placed, List<String> tiles) {
            List<String> left = new ArrayList<>(tiles);
            for (String word : placed) {
                if (!left.remove(word)) {
                    return false;
                }
            }
            return left.isEmpty();
        }
    }

    private static ItemFeedback gradeText(GivenAnswer given, List<String> accept, String show, LessonText explanation,
            AnswerNormaliser normaliser, LanguageChain language) {
        if (!(given instanceof GivenAnswer.Text text) || normaliser.normalise(text.value()).isEmpty()) {
            throw DomainException.of("answer", "Type an answer.");
        }
        String typed = normaliser.normalise(text.value());
        boolean correct = accept.stream().map(normaliser::normalise).anyMatch(typed::equals);
        String expected = show == null || show.isBlank() ? accept.get(0) : show;
        return new ItemFeedback(correct, expected, null, explanation.in(language));
    }
}
