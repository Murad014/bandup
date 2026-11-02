package com.bandup.api.grammar.application;

import java.util.List;

/**
 * A lesson as an AI pipeline or an admin sends it (the lesson format,
 * {@code lesson-format.md}). Every learner-facing text ({@code Object} here)
 * is either a string (English) or an object of languages
 * ({@code {"en": …, "az": …}}); {@link LessonInputBuilder} reads both.
 * Fields of the view that aren't input ({@code status}, {@code revision}, …)
 * are ignored, so a loaded lesson can be sent back as it is.
 *
 * @param id      the slug; used only when creating
 * @param version the version that was loaded; required when updating
 */
public record LessonInput(String id, Long version, String level, TopicInput topic, Object title, Object goal,
        Integer minutes, Integer position, List<LearnCardInput> learn, WorkedInput worked, List<ItemInput> practice,
        List<ItemInput> check) {

    public record TopicInput(String key, Object label) {
    }

    public record LearnCardInput(String icon, Object label, Object heading, Object body, List<Object> steps,
            List<ExampleInput> examples, Object tip) {
    }

    public record ExampleInput(Boolean ok, Object text) {
    }

    public record WorkedInput(Object question, List<Object> steps, String answer) {
    }

    /**
     * One item. Which fields count depends on {@code type}: {@code choose}
     * (options, answer = option index, whys), {@code type} (sentence, accept,
     * show), {@code fix} (wrong, accept, show), {@code build} (tiles, answer =
     * the sentence).
     */
    public record ItemInput(String id, String type, Object prompt, String sentence, String wrong, List<String> options,
            Object answer, List<Object> whys, List<String> accept, String show, List<String> tiles,
            Object explanation) {
    }
}
