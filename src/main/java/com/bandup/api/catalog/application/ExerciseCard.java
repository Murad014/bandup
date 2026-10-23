package com.bandup.api.catalog.application;

import java.util.List;

/**
 * One published exercise in the catalog. Never holds answers.
 *
 * @param id            the slug (/reading/{id})
 * @param questionCount question numbers (a "Choose TWO" counts twice, as on the page)
 * @param types         question type codes, in the order of their groups
 * @param done          the signed-in learner has attempted it (false when anonymous)
 * @param bestScore     their best attempt, or null
 */
public record ExerciseCard(String id, String title, String skill, String format, String level, Integer durationSeconds,
        int questionCount, List<String> types, List<Topic> topics, boolean done, Score bestScore) {

    public ExerciseCard {
        types = List.copyOf(types);
        topics = List.copyOf(topics);
    }

    /** A topic with its name in the help language (falls back along the language chain; else the slug). */
    public record Topic(String slug, String name) {
    }

    public record Score(int score, int total) {
    }
}
