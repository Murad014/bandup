package com.bandup.api.vocabulary.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.experimental.UtilityClass;

import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionWord;
import com.bandup.api.vocabulary.domain.session.valueobject.Task;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskId;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** A session's words, tasks, queue and mastered tasks as stored in {@code vocab_session.content}. */
@UtilityClass
final class SessionJson {

    record Content(List<SessionWord> words, List<Task> tasks, List<TaskId> queue, List<TaskId> mastered) {
    }

    static String write(List<SessionWord> words, List<Task> tasks, List<TaskId> queue, Iterable<TaskId> mastered) {
        ObjectNode root = CardJson.JSON.createObjectNode();
        ArrayNode w = root.putArray("words");
        words.forEach(word -> w.add(word(word)));
        ArrayNode t = root.putArray("tasks");
        tasks.forEach(task -> t.add(task(task)));
        ArrayNode q = root.putArray("queue");
        queue.forEach(id -> q.add(id.value()));
        ArrayNode m = root.putArray("mastered");
        mastered.forEach(id -> m.add(id.value()));
        return CardJson.JSON.writeValueAsString(root);
    }

    static Content read(String json) {
        JsonNode root = CardJson.JSON.readTree(json);
        List<SessionWord> words = new ArrayList<>();
        root.path("words").forEach(n -> words.add(word(n)));
        List<Task> tasks = new ArrayList<>();
        root.path("tasks").forEach(n -> tasks.add(task(n)));
        return new Content(words, tasks, ids(root.path("queue")), ids(root.path("mastered")));
    }

    private static ObjectNode word(SessionWord w) {
        ObjectNode n = CardJson.JSON.createObjectNode();
        n.put("saved", w.saved().value().toString());
        n.put("word", w.word());
        strings(n.putArray("forms"), w.forms());
        n.put("pos", w.pos());
        n.put("level", w.level());
        n.put("ipa", w.ipa());
        n.put("meaningEn", w.meaningEn());
        n.put("meaningAz", w.meaningAz());
        strings(n.putArray("examples"), w.examples());
        strings(n.putArray("collocations"), w.collocations());
        strings(n.putArray("family"), w.family());
        n.put("sentence", w.sentence());
        n.put("from", w.from());
        n.put("gapSentence", w.gapSentence());
        n.put("gapAnswer", w.gapAnswer());
        n.put("pairSentence", w.pairSentence());
        strings(n.putArray("pairOptions"), w.pairOptions());
        n.put("pairAnswer", w.pairAnswer());
        strings(n.putArray("wrongMeanings"), w.wrongMeanings());
        n.put("box", w.box());
        return n;
    }

    private static SessionWord word(JsonNode n) {
        return new SessionWord(new SavedWordId(UUID.fromString(n.path("saved").asString())), s(n, "word"),
                CardJson.strings(n.path("forms")), s(n, "pos"), s(n, "level"), s(n, "ipa"), s(n, "meaningEn"),
                s(n, "meaningAz"), CardJson.strings(n.path("examples")), CardJson.strings(n.path("collocations")),
                CardJson.strings(n.path("family")), nullable(n, "sentence"), nullable(n, "from"), s(n, "gapSentence"),
                s(n, "gapAnswer"), s(n, "pairSentence"), CardJson.strings(n.path("pairOptions")),
                n.path("pairAnswer").asInt(), CardJson.strings(n.path("wrongMeanings")), n.path("box").asInt());
    }

    private static ObjectNode task(Task task) {
        ObjectNode n = CardJson.JSON.createObjectNode();
        n.put("type", task.type());
        n.put("id", task.id().value());
        if (task.word() != null) {
            n.put("word", task.word().value().toString());
        }
        switch (task) {
            case Task.ChooseMeaning t -> {
                n.put("term", t.term());
                strings(n.putArray("options"), t.options());
                n.put("answer", t.answer());
            }
            case Task.Match t -> {
                strings(n.putArray("terms"), t.terms());
                strings(n.putArray("meanings"), t.meanings());
                ArrayNode a = n.putArray("answer");
                t.answer().forEach(a::add);
            }
            case Task.Gap t -> {
                n.put("sentence", t.sentence());
                n.put("hint", t.hint());
                n.put("answer", t.answer());
            }
            case Task.TypeFromMeaning t -> {
                n.put("meaning", t.meaning());
                n.put("hint", t.hint());
                strings(n.putArray("accept"), t.accept());
            }
            case Task.Listen t -> {
                n.put("speak", t.speak());
                strings(n.putArray("accept"), t.accept());
            }
            case Task.Pair t -> {
                n.put("sentence", t.sentence());
                strings(n.putArray("options"), t.options());
                n.put("answer", t.answer());
            }
            case Task.Translate t -> {
                n.put("az", t.az());
                n.put("hint", t.hint());
                strings(n.putArray("accept"), t.accept());
            }
        }
        return n;
    }

    private static Task task(JsonNode n) {
        TaskId id = new TaskId(s(n, "id"));
        SavedWordId word = n.hasNonNull("word") ? new SavedWordId(UUID.fromString(n.path("word").asString())) : null;
        return switch (s(n, "type")) {
            case "meaning" -> new Task.ChooseMeaning(id, word, s(n, "term"), CardJson.strings(n.path("options")),
                    n.path("answer").asInt());
            case "match" -> {
                List<Integer> answer = new ArrayList<>();
                n.path("answer").forEach(a -> answer.add(a.asInt()));
                yield new Task.Match(id, CardJson.strings(n.path("terms")), CardJson.strings(n.path("meanings")), answer);
            }
            case "gap" -> new Task.Gap(id, word, s(n, "sentence"), s(n, "hint"), s(n, "answer"));
            case "type" -> new Task.TypeFromMeaning(id, word, s(n, "meaning"), s(n, "hint"), CardJson.strings(n.path("accept")));
            case "listen" -> new Task.Listen(id, word, s(n, "speak"), CardJson.strings(n.path("accept")));
            case "pair" -> new Task.Pair(id, word, s(n, "sentence"), CardJson.strings(n.path("options")),
                    n.path("answer").asInt());
            case "translate" -> new Task.Translate(id, word, s(n, "az"), s(n, "hint"), CardJson.strings(n.path("accept")));
            default -> throw new IllegalStateException("Unknown task type " + s(n, "type"));
        };
    }

    private static List<TaskId> ids(JsonNode array) {
        List<TaskId> ids = new ArrayList<>();
        array.forEach(n -> ids.add(new TaskId(n.asString())));
        return ids;
    }

    private static String s(JsonNode n, String field) {
        return n.path(field).isString() ? n.path(field).asString() : "";
    }

    private static String nullable(JsonNode n, String field) {
        return n.path(field).isString() ? n.path(field).asString() : null;
    }

    private static void strings(ArrayNode array, List<String> values) {
        values.forEach(array::add);
    }
}
