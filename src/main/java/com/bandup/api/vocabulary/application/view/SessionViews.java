package com.bandup.api.vocabulary.application.view;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response records of the session API (design §5.1). Tasks never carry their
 * answers; review tasks never name their word.
 */
public final class SessionViews {

    private SessionViews() {
    }

    /** @param words LEARN only: the words to meet; empty for REVIEW */
    public record SessionView(UUID sessionId, String kind, List<MeetWord> words, List<TaskView> tasks,
            List<String> queue, int mastered, int total, boolean finished) {
    }

    /** One word's Meet card. {@code sentence}/{@code from}: where the learner saved it; may be null. */
    public record MeetWord(String word, String pos, String level, String ipa, String meaningEn, String meaningAz,
            List<String> examples, List<String> collocations, List<String> family, String sentence, String from) {
    }

    /**
     * One task, without its answer. Fields that don't belong to the type are null.
     *
     * @param term     meaning: the word whose meaning is asked
     * @param speak    listen: the word the browser speaks (learn sessions only)
     * @param from     review: where the word came from (never the word itself)
     */
    public record TaskView(String id, String type, String phase, String term, List<String> options, String sentence,
            String hint, String meaning, String az, String speak, List<String> terms, List<String> meanings,
            String from, Integer box) {
    }

    /** The server's answer to one task. {@code schedule}: review only. */
    public record AnswerView(boolean correct, String expected, String why, String explanation, int mastered,
            int total, boolean finished, ScheduleView schedule) {
    }

    /**
     * A reviewed word's new schedule.
     *
     * @param nextInDays days from the server's today to {@code nextDueOn} (the page never counts with its own clock);
     *                   null once mastered
     * @param changed    false when it was already answered today
     */
    public record ScheduleView(int from, int box, LocalDate nextDueOn, Integer nextInDays, boolean changed,
            boolean mastered) {
    }
}
