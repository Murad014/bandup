package com.bandup.api.grammar.application.view;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.experimental.UtilityClass;

/**
 * What learners receive (grammar design §5.1). None of these records has a
 * field for an answer, accepted value, why or explanation before the learner
 * answers (requirement 7); {@code GrammarKeysTest} checks it.
 */
@UtilityClass
public final class LearnerViews {

    public record LevelView(String level, String name, int lessonCount) {
    }

    public record CourseView(String level, int available, int finished, NextLesson next, ReviewInfo review,
            List<TopicView> topics, List<CourseLesson> lessons, int page, int size, int total) {
    }

    public record NextLesson(String slug, String title) {
    }

    /**
     * Null for guests.
     *
     * @param nextDueOn  when nothing is due now: the next day something is
     * @param nextInDays days from the server's today to {@code nextDueOn} (the page never counts with its own clock)
     */
    public record ReviewInfo(int due, LocalDate nextDueOn, Integer nextInDays) {
    }

    public record TopicView(String key, String label) {
    }

    /** @param lockedReason TOMORROW, FINISH_PREVIOUS or SIGN_IN; null unless LOCKED */
    public record CourseLesson(String slug, int position, String title, TopicView topic, int minutes, String state,
            String lockedReason) {
    }

    public record ProgressView(String level) {
    }

    /** A lesson to study. The worked example is a teaching step, so its answer is included (requirement 7.2). */
    public record LessonView(String slug, String level, int position, String title, String goal, int minutes,
            TopicView topic, String state, List<LearnCardView> learn, WorkedView worked, int practiceCount,
            int checkCount) {
    }

    public record LearnCardView(String icon, String label, String heading, String body, List<String> steps,
            List<ExampleView> examples, String tip) {
    }

    public record ExampleView(boolean ok, String text) {
    }

    public record WorkedView(String question, List<String> steps, String answer) {
    }

    public record RunView(UUID runId, List<ItemView> practice, List<ItemView> check) {
    }

    /** An item to answer: no keys. {@code tiles} are shuffled; the other optional fields belong to their type. */
    public record ItemView(String id, String type, String prompt, String sentence, String wrong, List<String> options,
            List<String> tiles) {
    }

    /**
     * Feedback on one lesson answer.
     *
     * @param expected the correct answer
     * @param why      for a wrong choose option; may be null
     */
    public record AnswerView(boolean correct, String expected, String why, String explanation, int mastered,
            int total, int retries, boolean finished) {
    }

    public record ReviewView(List<ReviewCardView> cards, int dueTotal) {
    }

    public record ReviewCardView(UUID cardId, TopicView topic, String lessonTitle, ItemView item) {
    }

    /**
     * @param nextInDays days from the server's today to {@code nextDueOn}
     * @param changed    false when the card was already answered today (its schedule stays)
     */
    public record ReviewAnswerView(boolean correct, String expected, String why, String explanation, int box,
            LocalDate nextDueOn, int nextInDays, boolean changed) {
    }
}
