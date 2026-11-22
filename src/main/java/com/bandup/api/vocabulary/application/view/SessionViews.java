package com.bandup.api.vocabulary.application.view;

import java.util.List;
import java.util.UUID;

/**
 * Response records of the session API (design §5.1). Tasks never carry their
 * answers; round tasks never name their word.
 */
public final class SessionViews {

    private SessionViews() {
    }

    /**
     * @param words LEARN only: the words to meet; empty for ROUND
     * @param set   ROUND only: which set and round this is
     */
    public record SessionView(UUID sessionId, String kind, List<MeetWord> words, List<TaskView> tasks,
            List<String> queue, int mastered, int total, boolean finished, RoundView set) {
    }

    /** @param number the set's number, @param count how many sets there are, @param round this round's number */
    public record RoundView(int number, int count, int round) {
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
     */
    public record TaskView(String id, String type, String phase, String term, List<String> options, String sentence,
            String hint, String meaning, String az, String speak, List<String> terms, List<String> meanings) {
    }

    /** The server's answer to one task. {@code step}: ROUND only, on a word's first try in the round. */
    public record AnswerView(boolean correct, String expected, String why, String explanation, int mastered,
            int total, boolean finished, StepView step) {
    }

    /**
     * A word's progress after its first try in a round (requirement 6.6).
     *
     * @param streak  rounds in a row with a right first try (0, 1, 2)
     * @param changed false when it was mastered already (in another tab)
     */
    public record StepView(int streak, boolean mastered, boolean changed) {
    }
}
