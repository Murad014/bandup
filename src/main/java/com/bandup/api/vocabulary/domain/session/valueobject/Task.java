package com.bandup.api.vocabulary.domain.session.valueobject;

import java.util.List;
import java.util.function.UnaryOperator;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;

/**
 * One task of a session (vocabulary requirements 5.4, 5.5, 6). Each type
 * grades its own answer; the answer never leaves the server.
 */
@ValueObject
public sealed interface Task {

    TaskId id();

    /** The word the task is about; null for a match of all words. */
    SavedWordId word();

    TaskPhase phase();

    String type();

    /**
     * @param normalise the answer normaliser
     * @throws DomainException (field {@code answer}) when the answer has the wrong kind
     */
    Grade grade(GivenAnswer answer, UnaryOperator<String> normalise);

    /** Choose the meaning of a word (practise). */
    @ValueObject
    record ChooseMeaning(TaskId id, SavedWordId word, String term, List<String> options, int answer) implements Task {

        public ChooseMeaning {
            options = List.copyOf(options);
        }

        public TaskPhase phase() {
            return TaskPhase.PRACTISE;
        }

        public String type() {
            return "meaning";
        }

        public Grade grade(GivenAnswer given, UnaryOperator<String> normalise) {
            int picked = choice(given, options.size());
            return new Grade(picked == answer, options.get(answer),
                    picked == answer ? null : "That isn't what “" + term + "” means.");
        }
    }

    /** Match every word of the session with its meaning (practise). */
    @ValueObject
    record Match(TaskId id, List<String> terms, List<String> meanings, List<Integer> answer) implements Task {

        public Match {
            terms = List.copyOf(terms);
            meanings = List.copyOf(meanings);
            answer = List.copyOf(answer);
        }

        public SavedWordId word() {
            return null;
        }

        public TaskPhase phase() {
            return TaskPhase.PRACTISE;
        }

        public String type() {
            return "match";
        }

        public Grade grade(GivenAnswer given, UnaryOperator<String> normalise) {
            if (!(given instanceof GivenAnswer.Pairs pairs) || pairs.picks().size() != terms.size()) {
                throw DomainException.of("answer", "Pick a meaning for each of the " + terms.size() + " words.");
            }
            StringBuilder expected = new StringBuilder();
            for (int i = 0; i < terms.size(); i++) {
                expected.append(i == 0 ? "" : "; ").append(terms.get(i)).append(" — ").append(meanings.get(answer.get(i)));
            }
            return new Grade(pairs.picks().equals(answer), expected.toString(), null);
        }
    }

    /** Type the word into a sentence's gap (master). */
    @ValueObject
    record Gap(TaskId id, SavedWordId word, String sentence, String hint, String answer) implements Task {

        public TaskPhase phase() {
            return TaskPhase.MASTER;
        }

        public String type() {
            return "gap";
        }

        public Grade grade(GivenAnswer given, UnaryOperator<String> normalise) {
            return new Grade(normalise.apply(text(given)).equals(normalise.apply(answer)), answer, null);
        }
    }

    /** Type the word from its meaning (master). */
    @ValueObject
    record TypeFromMeaning(TaskId id, SavedWordId word, String meaning, String hint, List<String> accept)
            implements Task {

        public TypeFromMeaning {
            accept = List.copyOf(accept);
        }

        public TaskPhase phase() {
            return TaskPhase.MASTER;
        }

        public String type() {
            return "type";
        }

        public Grade grade(GivenAnswer given, UnaryOperator<String> normalise) {
            return typed(given, accept, normalise);
        }
    }

    /** Listen and type (learn sessions only: the browser must know the word to speak it). */
    @ValueObject
    record Listen(TaskId id, SavedWordId word, String speak, List<String> accept) implements Task {

        public Listen {
            accept = List.copyOf(accept);
        }

        public TaskPhase phase() {
            return TaskPhase.MASTER;
        }

        public String type() {
            return "listen";
        }

        public Grade grade(GivenAnswer given, UnaryOperator<String> normalise) {
            return typed(given, accept, normalise);
        }
    }

    /** Choose the word that makes a natural phrase (master). */
    @ValueObject
    record Pair(TaskId id, SavedWordId word, String sentence, List<String> options, int answer) implements Task {

        public Pair {
            options = List.copyOf(options);
        }

        public TaskPhase phase() {
            return TaskPhase.MASTER;
        }

        public String type() {
            return "pair";
        }

        public Grade grade(GivenAnswer given, UnaryOperator<String> normalise) {
            int picked = choice(given, options.size());
            return new Grade(picked == answer, options.get(answer),
                    picked == answer ? null : "“" + options.get(picked) + "” doesn't fit here.");
        }
    }

    /** Type the English word for its Azerbaijani meaning (master). */
    @ValueObject
    record Translate(TaskId id, SavedWordId word, String az, String hint, List<String> accept) implements Task {

        public Translate {
            accept = List.copyOf(accept);
        }

        public TaskPhase phase() {
            return TaskPhase.MASTER;
        }

        public String type() {
            return "translate";
        }

        public Grade grade(GivenAnswer given, UnaryOperator<String> normalise) {
            return typed(given, accept, normalise);
        }
    }

    private static int choice(GivenAnswer given, int options) {
        if (!(given instanceof GivenAnswer.Choice c) || c.index() < 0 || c.index() >= options) {
            throw DomainException.of("answer", "Pick one of the " + options + " options.");
        }
        return c.index();
    }

    private static String text(GivenAnswer given) {
        if (!(given instanceof GivenAnswer.Text t) || t.value().isBlank() || t.value().length() > 100) {
            throw DomainException.of("answer", "Type the word.");
        }
        return t.value();
    }

    private static Grade typed(GivenAnswer given, List<String> accept, UnaryOperator<String> normalise) {
        String typed = normalise.apply(text(given));
        boolean correct = accept.stream().map(normalise).anyMatch(typed::equals);
        return new Grade(correct, accept.get(0), null);
    }
}
