package com.bandup.api.grammar.application.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.review.valueobject.ReviewCardId;

/**
 * Port: the learner's course read model (SQL; grammar design §4.3). Only
 * PUBLISHED lessons count. Texts come as language → text maps.
 */
public interface LearnerCourses {

    /** Published lessons per level code (every level present, 0 when none). */
    Map<String, Integer> lessonCounts();

    /** Facts about a whole course that one page can't show. */
    CourseFacts facts(String level, Optional<LearnerId> learner);

    /** One page of the course, in position order. @param topic null for every topic */
    CoursePage page(String level, String topic, int offset, int limit);

    /** The level's topics in course order (by their first lesson). */
    List<TopicEntry> topics(String level);

    /** Up to {@code limit} due cards of published lessons, oldest due first. */
    List<DueCard> dueCards(LearnerId learner, LocalDate today, int limit);

    /** How many cards are due and, when none, the next due day. */
    ReviewSummary reviewSummary(LearnerId learner, LocalDate today);

    /**
     * @param available       published lessons of the level
     * @param finished        of those, how many the learner finished (0 for a guest)
     * @param firstOfCourse   the lesson at the lowest position
     * @param firstUnfinished the learner's lowest-position unfinished lesson (guest: the first)
     */
    record CourseFacts(int available, int finished, Optional<LessonEntry> firstOfCourse,
            Optional<LessonEntry> firstUnfinished) {

        public Optional<LessonId> firstOfCourseId() {
            return firstOfCourse.map(LessonEntry::id);
        }

        public Optional<LessonId> firstUnfinishedId() {
            return firstUnfinished.map(LessonEntry::id);
        }
    }

    record CoursePage(List<LessonEntry> lessons, int total) {
    }

    record LessonEntry(LessonId id, String slug, int position, Map<String, String> title, String topicKey,
            Map<String, String> topicLabel, int minutes) {
    }

    record TopicEntry(String key, Map<String, String> label) {
    }

    record DueCard(ReviewCardId card, LessonId lesson, ItemId item) {
    }

    /** @param nextDueOn the next day with due cards when none are due now; may be null */
    record ReviewSummary(int due, LocalDate nextDueOn) {
    }
}
