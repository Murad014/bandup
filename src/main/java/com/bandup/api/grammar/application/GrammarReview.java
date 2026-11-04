package com.bandup.api.grammar.application;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.grammar.application.port.GrammarDays;
import com.bandup.api.grammar.application.port.LearnerCourses;
import com.bandup.api.grammar.application.port.LearnerCourses.DueCard;
import com.bandup.api.grammar.application.view.LearnerViews.ReviewAnswerView;
import com.bandup.api.grammar.application.view.LearnerViews.ReviewCardView;
import com.bandup.api.grammar.application.view.LearnerViews.ReviewView;
import com.bandup.api.grammar.application.view.LearnerViews.TopicView;
import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.repository.GrammarLessonRepository;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemFeedback;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.review.aggregate.ReviewCard;
import com.bandup.api.grammar.domain.review.repository.ReviewCardRepository;
import com.bandup.api.grammar.domain.review.valueobject.ReviewCardId;
import com.bandup.api.grammar.domain.review.valueobject.ReviewSchedule;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.application.UnauthenticatedException;
import com.bandup.api.shared.domain.language.LanguageChain;

/** The spaced review (grammar requirement 6): the due cards and answering one. */
@Service
@RequiredArgsConstructor
public class GrammarReview {

    /** Cards in one review session (requirement 6.3). */
    public static final int SESSION_SIZE = 20;

    private final LearnerCourses courses;
    private final GrammarLessonRepository lessons;
    private final ReviewCardRepository cards;
    private final Learner learner;
    private final GrammarDays days;

    /** Up to 20 due cards, oldest first, as items without keys. */
    @Transactional(readOnly = true)
    public ReviewView due(String language) {
        LearnerId me = signedIn();
        LanguageChain chain = learner.language(language);
        Map<LessonId, GrammarLesson> loaded = new HashMap<>();
        List<ReviewCardView> views = new ArrayList<>();
        for (DueCard due : courses.dueCards(me, days.today(), SESSION_SIZE)) {
            GrammarLesson lesson = loaded.computeIfAbsent(due.lesson(), id -> lessons.findById(id).orElseThrow());
            lesson.find(due.item()).ifPresent(item -> views.add(new ReviewCardView(due.card().value(),
                    new TopicView(lesson.body().topic().key(), lesson.body().topic().label().in(chain)),
                    lesson.body().title().in(chain),
                    LearnerItems.view(item, chain, due.card().value().getLeastSignificantBits()))));
        }
        return new ReviewView(views, courses.reviewSummary(me, days.today()).due());
    }

    /** Grades one review answer and moves the card (requirements 6.4–6.6). */
    @Transactional
    public ReviewAnswerView answer(UUID cardId, Object answer, String language) {
        LearnerId me = signedIn();
        LanguageChain chain = learner.language(language);
        ReviewCard card = cards.findById(new ReviewCardId(cardId)).filter(c -> c.belongsTo(me))
                .orElseThrow(GrammarReview::noSuchCard);
        GrammarLesson lesson = lessons.findById(card.lesson()).filter(GrammarLesson::isPublished)
                .orElseThrow(GrammarReview::noSuchCard);
        ItemFeedback feedback = lesson.grade(card.item(), LearnerItems.given(answer), LearnerItems.NORMALISER, chain);
        LocalDate today = days.today();
        ReviewSchedule schedule = card.answer(feedback.correct(), today);
        cards.save(card);
        return new ReviewAnswerView(feedback.correct(), feedback.expected(), feedback.why(), feedback.explanation(),
                schedule.box().value(), schedule.dueOn(), (int) ChronoUnit.DAYS.between(today, schedule.dueOn()),
                schedule.changed());
    }

    private LearnerId signedIn() {
        return learner.current().orElseThrow(() -> new UnauthenticatedException("Sign in to review."));
    }

    private static NotFoundException noSuchCard() {
        return new NotFoundException("No such review card.");
    }
}
