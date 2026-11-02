package com.bandup.api.grammar.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.grammar.application.port.GrammarDays;
import com.bandup.api.grammar.application.view.LearnerViews.AnswerView;
import com.bandup.api.grammar.application.view.LearnerViews.RunView;
import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.repository.GrammarLessonRepository;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemFeedback;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.progress.aggregate.GrammarProgress;
import com.bandup.api.grammar.domain.progress.repository.GrammarProgressRepository;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.review.aggregate.ReviewCard;
import com.bandup.api.grammar.domain.review.repository.ReviewCardRepository;
import com.bandup.api.grammar.domain.run.aggregate.LessonRun;
import com.bandup.api.grammar.domain.run.repository.LessonRunRepository;
import com.bandup.api.grammar.domain.run.valueobject.LessonRevision;
import com.bandup.api.grammar.domain.run.valueobject.RunId;
import com.bandup.api.grammar.domain.run.valueobject.RunOutcome;
import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.domain.language.LanguageChain;

/**
 * Doing a lesson (grammar requirements 4, 5, 8): start a run, then answer its
 * items one by one. Each answer is graded on the server. The answer that
 * masters the last check item finishes the run and, for a signed-in learner,
 * records the lesson as finished and puts its check items in the review deck,
 * all in one transaction.
 */
@Service
@RequiredArgsConstructor
public class LessonRuns {

    private final LessonAccess access;
    private final GrammarLessonRepository lessons;
    private final LessonRunRepository runs;
    private final GrammarProgressRepository progresses;
    private final ReviewCardRepository cards;
    private final Learner learner;
    private final GrammarDays days;
    private final Clock clock;

    /** Starts a run of a lesson the caller may open; the items come without keys. */
    @Transactional
    public RunView start(String slug, String language) {
        LanguageChain chain = learner.language(language);
        Optional<LearnerId> me = learner.current();
        GrammarLesson lesson = access.open(slug, me).lesson();
        LessonRun run = LessonRun.start(lesson.id(), new LessonRevision(lesson.revision().value()), me, clock.instant());
        runs.save(run);
        long seed = run.id().seed();
        return new RunView(run.id().value(), LearnerItems.views(lesson.body().practice(), chain, seed),
                LearnerItems.views(lesson.body().check(), chain, seed));
    }

    /**
     * Grades one answer of a run.
     *
     * @throws NotFoundException if the run doesn't exist or isn't the caller's
     * @throws ConflictException if the run is finished or the lesson changed since it started
     */
    @Transactional
    public AnswerView answer(UUID runId, String itemId, Object answer, String language) {
        LanguageChain chain = learner.language(language);
        Optional<LearnerId> me = learner.current();
        LessonRun run = runs.findById(new RunId(runId)).filter(r -> r.belongsTo(me))
                .orElseThrow(() -> new NotFoundException("No such lesson run."));
        if (run.isFinished()) {
            throw new ConflictException("This lesson run is finished. Start the lesson again to practise more.");
        }
        GrammarLesson lesson = lessons.findById(run.lesson())
                .filter(l -> l.isPublished() && l.revision().value() == run.lessonRevision().value())
                .orElseThrow(() -> new ConflictException("This lesson was updated. Please start it again."));
        ItemId item = new ItemId(itemId == null ? "" : itemId);
        ItemFeedback feedback = lesson.grade(item, LearnerItems.given(answer), LearnerItems.NORMALISER, chain);
        RunOutcome outcome = run.record(item, feedback.correct(), lesson.isCheckItem(item), lesson.checkItems().size(),
                clock.instant());
        runs.save(run);
        if (outcome.justFinished()) {
            run.learner().ifPresent(id -> finished(id, lesson));
        }
        return new AnswerView(feedback.correct(), feedback.expected(), feedback.why(), feedback.explanation(),
                outcome.mastered(), outcome.total(), outcome.retries(), outcome.finished());
    }

    /** Records the finished lesson and adds its check items to the deck (requirements 6.1, 8.1). */
    private void finished(LearnerId id, GrammarLesson lesson) {
        GrammarProgress progress = access.progress(id);
        progress.finish(lesson.id(), Learner.courseLevel(lesson.level()), days.today());
        progresses.save(progress);
        Set<ItemId> inDeck = cards.itemsInDeck(id, lesson.id());
        List<ReviewCard> added = new ArrayList<>();
        for (ItemId item : lesson.checkItems()) {
            if (!inDeck.contains(item)) {
                added.add(ReviewCard.create(id, lesson.id(), item, days.today()));
            }
        }
        cards.saveAll(added);
    }
}
