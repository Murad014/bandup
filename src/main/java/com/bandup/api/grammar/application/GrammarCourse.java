package com.bandup.api.grammar.application;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.grammar.application.port.GrammarDays;
import com.bandup.api.grammar.application.port.LearnerCourses;
import com.bandup.api.grammar.application.port.LearnerCourses.CourseFacts;
import com.bandup.api.grammar.application.port.LearnerCourses.CoursePage;
import com.bandup.api.grammar.application.port.LearnerCourses.LessonEntry;
import com.bandup.api.grammar.application.port.LearnerCourses.ReviewSummary;
import com.bandup.api.grammar.application.view.LearnerViews.CourseLesson;
import com.bandup.api.grammar.application.view.LearnerViews.CourseView;
import com.bandup.api.grammar.application.view.LearnerViews.ExampleView;
import com.bandup.api.grammar.application.view.LearnerViews.LearnCardView;
import com.bandup.api.grammar.application.view.LearnerViews.LessonView;
import com.bandup.api.grammar.application.view.LearnerViews.LevelView;
import com.bandup.api.grammar.application.view.LearnerViews.NextLesson;
import com.bandup.api.grammar.application.view.LearnerViews.ProgressView;
import com.bandup.api.grammar.application.view.LearnerViews.ReviewInfo;
import com.bandup.api.grammar.application.view.LearnerViews.TopicView;
import com.bandup.api.grammar.application.view.LearnerViews.WorkedView;
import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.LearnCard;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonText;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;
import com.bandup.api.grammar.domain.progress.aggregate.GrammarProgress;
import com.bandup.api.grammar.domain.progress.repository.GrammarProgressRepository;
import com.bandup.api.grammar.domain.progress.service.CourseUnlocking;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.progress.valueobject.LessonState;
import com.bandup.api.shared.application.UnauthenticatedException;
import com.bandup.api.shared.domain.language.LanguageChain;

/**
 * The learner's course (grammar requirements 1–3): levels, the course page
 * with each lesson's state, the chosen level, and a lesson to study.
 */
@Service
@RequiredArgsConstructor
public class GrammarCourse {

    public static final int MAX_PAGE_SIZE = 100;
    private static final CourseUnlocking UNLOCKING = new CourseUnlocking();

    private final LearnerCourses courses;
    private final GrammarProgressRepository progresses;
    private final LessonAccess access;
    private final Learner learner;
    private final GrammarDays days;

    /** Every level with its number of published lessons (requirement 1.1). */
    @Transactional(readOnly = true)
    public List<LevelView> levels() {
        Map<String, Integer> counts = courses.lessonCounts();
        return Arrays.stream(GrammarLevel.values())
                .map(level -> new LevelView(level.name(), level.displayName(), counts.getOrDefault(level.name(), 0)))
                .toList();
    }

    /** One page of a level's course with each lesson's state (requirement 2). */
    @Transactional
    public CourseView course(String levelCode, String topic, int page, int size, String language) {
        GrammarLevel level = Learner.level(levelCode);
        LanguageChain chain = learner.language(language);
        Optional<LearnerId> me = learner.current();
        Optional<GrammarProgress> progress = me.map(access::progress);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 0);
        CourseFacts facts = courses.facts(level.name(), me);
        CoursePage lessons = courses.page(level.name(), blankToNull(topic), pageNumber * pageSize, pageSize);
        List<LessonState> states = UNLOCKING.states(lessons.lessons().stream().map(LessonEntry::id).toList(), progress,
                Learner.courseLevel(level), facts.firstUnfinishedId(), facts.firstOfCourseId(), days.today());
        List<CourseLesson> entries = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            LessonEntry entry = lessons.lessons().get(i);
            LessonState state = states.get(i);
            entries.add(new CourseLesson(entry.slug(), entry.position(), Learner.pick(entry.title(), chain),
                    new TopicView(entry.topicKey(), Learner.pick(entry.topicLabel(), chain)), entry.minutes(),
                    state.state().name(), state.lockReason().map(Enum::name).orElse(null)));
        }
        NextLesson next = (me.isPresent() ? facts.firstUnfinished() : facts.firstOfCourse())
                .map(e -> new NextLesson(e.slug(), Learner.pick(e.title(), chain))).orElse(null);
        ReviewInfo review = me.map(id -> {
            LocalDate today = days.today();
            ReviewSummary summary = courses.reviewSummary(id, today);
            return new ReviewInfo(summary.due(), summary.nextDueOn(),
                    summary.nextDueOn() == null ? null : (int) ChronoUnit.DAYS.between(today, summary.nextDueOn()));
        }).orElse(null);
        List<TopicView> topics = courses.topics(level.name()).stream()
                .map(t -> new TopicView(t.key(), Learner.pick(t.label(), chain))).toList();
        return new CourseView(level.name(), facts.available(), facts.finished(), next, review, topics, entries,
                pageNumber, pageSize, lessons.total());
    }

    /** The signed-in learner's chosen level (requirement 1.2). */
    @Transactional
    public ProgressView me() {
        LearnerId me = signedIn();
        return new ProgressView(progresses.findByLearner(me).flatMap(GrammarProgress::level).map(Enum::name).orElse(null));
    }

    /** Saves the signed-in learner's level; progress in every level is kept (requirement 1.4). */
    @Transactional
    public void chooseLevel(String levelCode) {
        GrammarLevel level = Learner.level(levelCode);
        LearnerId me = signedIn();
        GrammarProgress progress = access.progress(me);
        progress.chooseLevel(Learner.courseLevel(level));
        progresses.save(progress);
    }

    /** A lesson to study, if the caller may open it (requirements 3.5, 4). */
    @Transactional
    public LessonView lesson(String slug, String language) {
        LanguageChain chain = learner.language(language);
        LessonAccess.Opened opened = access.open(slug, learner.current());
        GrammarLesson lesson = opened.lesson();
        LessonBody b = lesson.body();
        return new LessonView(lesson.slug().value(), b.level().name(), lesson.position().map(Position::value).orElse(0),
                b.title().in(chain), b.goal().in(chain), b.minutes(),
                new TopicView(b.topic().key(), b.topic().label().in(chain)), opened.state().state().name(),
                b.learn().stream().map(card -> card(card, chain)).toList(),
                new WorkedView(b.worked().question().in(chain), texts(b.worked().steps(), chain), b.worked().answer()),
                b.practice().size(), b.check().size());
    }

    private static LearnCardView card(LearnCard card, LanguageChain chain) {
        return new LearnCardView(card.icon(), card.label().in(chain), card.heading().in(chain),
                card.bodyText().map(t -> t.in(chain)).orElse(null), texts(card.steps(), chain),
                card.examples().stream().map(e -> new ExampleView(e.ok(), e.text().in(chain))).toList(),
                card.tipText().map(t -> t.in(chain)).orElse(null));
    }

    private static List<String> texts(List<LessonText> texts, LanguageChain chain) {
        return texts.stream().map(t -> t.in(chain)).toList();
    }

    private LearnerId signedIn() {
        return learner.current().orElseThrow(() -> new UnauthenticatedException("Sign in first."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
