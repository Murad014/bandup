package com.bandup.api.vocabulary.domain.session.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.bandup.api.shared.domain.ddd.DomainService;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionKind;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionWord;
import com.bandup.api.vocabulary.domain.session.valueobject.Task;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskId;

/**
 * Builds a session's tasks from its word snapshots (vocabulary requirements
 * 5.4, 5.5, 6). LEARN: choose the meaning of the first word, a match of all
 * words (3+, all with different meanings), then one master task per word, rotating gap / type from meaning
 * / listen / pair / translate. REVIEW: one task per word, rotating pair / gap
 * / type from meaning / translate — never listen, which would give the word
 * to the browser. Options are shuffled with the session's seed.
 */
@DomainService
public final class TaskBuilder {

    private static final List<String> LEARN_ROTATION = List.of("gap", "type", "listen", "pair", "translate");
    private static final List<String> REVIEW_ROTATION = List.of("pair", "gap", "type", "translate");

    public List<Task> build(SessionKind kind, List<SessionWord> words, long seed) {
        if (words.isEmpty()) {
            throw new IllegalArgumentException("A session needs words");
        }
        Random random = new Random(seed);
        List<Task> tasks = new ArrayList<>();
        if (kind == SessionKind.LEARN) {
            tasks.add(chooseMeaning(TaskId.of(1), words.get(0), random));
            boolean distinct = words.stream().map(SessionWord::meaningEn).distinct().count() == words.size();
            if (words.size() >= 3 && distinct) {
                tasks.add(match(TaskId.of(2), words, random));
            }
        }
        for (int i = 0; i < words.size(); i++) {
            SessionWord word = words.get(i);
            String type = kind == SessionKind.LEARN ? LEARN_ROTATION.get(i % LEARN_ROTATION.size())
                    : REVIEW_ROTATION.get((i + word.box()) % REVIEW_ROTATION.size());
            tasks.add(task(type, TaskId.of(tasks.size() + 1), word, random));
        }
        return List.copyOf(tasks);
    }

    private Task task(String type, TaskId id, SessionWord w, Random random) {
        return switch (type) {
            case "gap" -> new Task.Gap(id, w.saved(), w.gapSentence(), hint(w.gapAnswer()), w.gapAnswer());
            case "type" -> new Task.TypeFromMeaning(id, w.saved(), w.meaningEn(), hint(w.word()), accepted(w));
            case "listen" -> new Task.Listen(id, w.saved(), w.word(), accepted(w));
            case "pair" -> pair(id, w, random);
            case "translate" -> new Task.Translate(id, w.saved(), w.meaningAz(), hint(w.word()), accepted(w));
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static Task.ChooseMeaning chooseMeaning(TaskId id, SessionWord w, Random random) {
        List<String> options = new ArrayList<>(w.wrongMeanings());
        options.add(w.meaningEn());
        Collections.shuffle(options, random);
        return new Task.ChooseMeaning(id, w.saved(), w.word(), options, options.indexOf(w.meaningEn()));
    }

    private static Task.Match match(TaskId id, List<SessionWord> words, Random random) {
        List<String> meanings = new ArrayList<>(words.stream().map(SessionWord::meaningEn).toList());
        Collections.shuffle(meanings, random);
        List<Integer> answer = words.stream().map(w -> meanings.indexOf(w.meaningEn())).toList();
        return new Task.Match(id, words.stream().map(SessionWord::word).toList(), meanings, answer);
    }

    private static Task.Pair pair(TaskId id, SessionWord w, Random random) {
        String right = w.pairOptions().get(w.pairAnswer());
        List<String> options = new ArrayList<>(w.pairOptions());
        Collections.shuffle(options, random);
        return new Task.Pair(id, w.saved(), w.pairSentence(), options, options.indexOf(right));
    }

    /** The base form first (shown as the right answer), then the other forms. */
    private static List<String> accepted(SessionWord w) {
        Set<String> accept = new LinkedHashSet<>();
        accept.add(w.word());
        accept.addAll(w.forms());
        return List.copyOf(accept);
    }

    /** "riverbank" → "r _ _ _ _ _ _ _ _"; spaces, hyphens and apostrophes stay. */
    public static String hint(String word) {
        StringBuilder hint = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (i > 0) {
                hint.append(' ');
            }
            hint.append(i == 0 || c == ' ' || c == '-' || c == '\'' ? c : '_');
        }
        return hint.toString();
    }
}
