package com.bandup.api.grammar.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The full lesson for admins (grammar design §5.2): the lesson format with
 * answer keys, every text as a language object, plus its state.
 */
public record AdminLessonView(String id, long version, String status, int revision, Integer position,
        Instant publishedAt, String level, Topic topic, Map<String, String> title, Map<String, String> goal,
        int minutes, List<LearnCard> learn, Worked worked, List<Item> practice, List<Item> check) {

    public record Topic(String key, Map<String, String> label) {
    }

    public record LearnCard(String icon, Map<String, String> label, Map<String, String> heading,
            Map<String, String> body, List<Map<String, String>> steps, List<Example> examples, Map<String, String> tip) {
    }

    public record Example(boolean ok, Map<String, String> text) {
    }

    public record Worked(Map<String, String> question, List<Map<String, String>> steps, String answer) {
    }

    /** Fields that don't belong to the item's type are null. {@code answer}: index (choose) or sentence (build). */
    public record Item(String id, String type, Map<String, String> prompt, String sentence, String wrong,
            List<String> options, Object answer, List<Map<String, String>> whys, List<String> accept, String show,
            List<String> tiles, Map<String, String> explanation) {
    }
}
