package com.bandup.api.vocabulary.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.application.port.LearnerWords;
import com.bandup.api.vocabulary.application.port.PassageContext;
import com.bandup.api.vocabulary.application.port.VocabularyDays;
import com.bandup.api.vocabulary.application.view.SessionViews.AnswerView;
import com.bandup.api.vocabulary.application.view.SessionViews.MeetWord;
import com.bandup.api.vocabulary.application.view.SessionViews.ScheduleView;
import com.bandup.api.vocabulary.application.view.SessionViews.SessionView;
import com.bandup.api.vocabulary.application.view.SessionViews.TaskView;
import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.repository.SavedWordRepository;
import com.bandup.api.vocabulary.domain.saved.valueobject.Box;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.ReviewSchedule;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordState;
import com.bandup.api.vocabulary.domain.session.aggregate.WordSession;
import com.bandup.api.vocabulary.domain.session.repository.WordSessionRepository;
import com.bandup.api.vocabulary.domain.session.valueobject.GivenAnswer;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionId;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionKind;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionWord;
import com.bandup.api.vocabulary.domain.session.valueobject.Task;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskFeedback;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskId;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

/**
 * Learn and review sessions (vocabulary requirements 5–7). Words are
 * snapshotted when the session starts; every answer is graded on the server.
 * A review answer moves its word (first answer of the day only); a finished
 * learn session puts its words in box 1.
 */
@Service
@RequiredArgsConstructor
public class WordSessions {

    public static final int LEARN_WORDS = 5;
    public static final int REVIEW_WORDS = 20;

    private final Learner learner;
    private final LearnerWords words;
    private final SavedWordRepository saved;
    private final WordCardRepository cards;
    private final WordSessionRepository sessions;
    private final PassageContext passages;
    private final VocabularyDays days;
    private final Clock clock;

    @Transactional
    public SessionView start(String kind, String exercise) {
        LearnerId me = learner.required();
        SessionKind k = kind(kind);
        List<UUID> ids = k == SessionKind.LEARN ? words.learnable(me, blank(exercise), LEARN_WORDS)
                : words.due(me, days.today(), REVIEW_WORDS);
        if (ids.isEmpty()) {
            throw DomainException.of("kind", k == SessionKind.LEARN ? "There are no new words ready to learn."
                    : "Nothing is due for review.");
        }
        List<SessionWord> snapshots = new ArrayList<>();
        for (UUID id : ids) {
            SavedWord word = saved.findById(new SavedWordId(id)).orElseThrow();
            cards.findById(word.word()).filter(WordCard::isReady).ifPresent(card -> snapshots.add(snapshot(word, card)));
        }
        SessionId id = SessionId.random();
        WordSession session = WordSession.start(id, me, k, snapshots,
                VocabularyRules.TASKS.build(k, snapshots, id.seed()), clock.instant());
        sessions.save(session);
        return view(session);
    }

    @Transactional
    public SessionView get(UUID sessionId) {
        return view(mine(sessionId));
    }

    @Transactional
    public AnswerView answer(UUID sessionId, String taskId, Object answer) {
        WordSession session = mine(sessionId);
        if (session.isFinished()) {
            throw new ConflictException("This session is finished.");
        }
        TaskId task;
        try {
            task = new TaskId(taskId == null ? "" : taskId);
        } catch (IllegalArgumentException e) {
            throw DomainException.of("taskId", "This task isn't waiting for an answer.");
        }
        TaskFeedback f = session.answer(task, given(answer), VocabularyRules.ANSWERS::normalise, clock.instant());
        LocalDate today = days.today();
        ScheduleView schedule = null;
        if (session.kind() == SessionKind.REVIEW && f.word() != null) {
            schedule = saved.findById(f.word()).filter(w -> w.state() == WordState.LEARNING)
                    .map(w -> move(w, f.correct(), today)).orElse(null);
        }
        if (session.kind() == SessionKind.LEARN && f.finished()) {
            for (SessionWord w : session.words()) {
                saved.findById(w.saved()).ifPresent(word -> {
                    word.learned(today);
                    saved.save(word);
                });
            }
        }
        sessions.save(session);
        return new AnswerView(f.correct(), f.expected(), f.why(), f.explanation(), f.mastered(), f.total(),
                f.finished(), schedule);
    }

    private ScheduleView move(SavedWord word, boolean correct, LocalDate today) {
        ReviewSchedule s = word.answer(correct, today);
        saved.save(word);
        return new ScheduleView(s.from().value(), s.box().value(), s.dueOn(),
                s.dueOn() == null ? null : (int) ChronoUnit.DAYS.between(today, s.dueOn()), s.changed(),
                s.state() == WordState.MASTERED);
    }

    private WordSession mine(UUID sessionId) {
        LearnerId me = learner.required();
        return sessions.findById(new SessionId(sessionId)).filter(s -> s.belongsTo(me))
                .orElseThrow(() -> new NotFoundException("No such session."));
    }

    private SessionWord snapshot(SavedWord word, WordCard card) {
        CardContent c = card.content().orElseThrow();
        String from = word.source().exerciseSlug().flatMap(passages::title).orElse(null);
        return new SessionWord(word.id(), card.display(), card.forms().stream().map(WordForm::value).toList(), c.pos(),
                c.level(), c.ipa(), c.meaning().en(), c.meaning().az(), c.examples(), c.collocations(), c.family(),
                word.source().sentenceText().orElse(null), from, c.gap().sentence(), c.gap().answer(),
                c.pair().sentence(), c.pair().options(), c.pair().answer(), c.wrongMeanings(),
                word.box().map(Box::value).orElse(0));
    }

    private static SessionView view(WordSession s) {
        boolean learn = s.kind() == SessionKind.LEARN;
        List<MeetWord> meet = learn ? s.words().stream().map(w -> new MeetWord(w.word(), w.pos(), w.level(), w.ipa(),
                w.meaningEn(), w.meaningAz(), w.examples(), w.collocations(), w.family(), w.sentence(), w.from())).toList()
                : List.of();
        List<TaskView> tasks = s.tasks().stream().map(t -> task(t, s)).toList();
        return new SessionView(s.id().value(), s.kind().name(), meet, tasks,
                s.queue().stream().map(TaskId::value).toList(), s.mastered().size(), s.masterTotal(), s.isFinished());
    }

    private static TaskView task(Task task, WordSession s) {
        Optional<SessionWord> word = s.words().stream().filter(w -> w.saved().equals(task.word())).findFirst();
        String from = s.kind() == SessionKind.REVIEW ? word.map(w -> w.from() == null ? "your words" : w.from()).orElse(null)
                : null;
        Integer box = s.kind() == SessionKind.REVIEW ? word.map(SessionWord::box).orElse(null) : null;
        String id = task.id().value();
        String phase = task.phase().name();
        return switch (task) {
            case Task.ChooseMeaning t -> new TaskView(id, t.type(), phase, t.term(), t.options(), null, null, null, null,
                    null, null, null, from, box);
            case Task.Match t -> new TaskView(id, t.type(), phase, null, null, null, null, null, null, null, t.terms(),
                    t.meanings(), from, box);
            case Task.Gap t -> new TaskView(id, t.type(), phase, null, null, t.sentence(), t.hint(), null, null, null,
                    null, null, from, box);
            case Task.TypeFromMeaning t -> new TaskView(id, t.type(), phase, null, null, null, t.hint(), t.meaning(),
                    null, null, null, null, from, box);
            case Task.Listen t -> new TaskView(id, t.type(), phase, null, null, null, null, null, null, t.speak(), null,
                    null, from, box);
            case Task.Pair t -> new TaskView(id, t.type(), phase, null, t.options(), t.sentence(), null, null, null,
                    null, null, null, from, box);
            case Task.Translate t -> new TaskView(id, t.type(), phase, null, null, null, t.hint(), null, t.az(), null,
                    null, null, from, box);
        };
    }

    /** JSON → answer: a number (option), a string (typed) or a list of numbers (match). */
    private static GivenAnswer given(Object answer) {
        if (answer instanceof Integer index) {
            return new GivenAnswer.Choice(index);
        }
        if (answer instanceof String text) {
            return new GivenAnswer.Text(text);
        }
        if (answer instanceof List<?> list && list.stream().allMatch(Integer.class::isInstance)) {
            return new GivenAnswer.Pairs(list.stream().map(Integer.class::cast).toList());
        }
        throw DomainException.of("answer", "Give an option number, a typed word, or a list of option numbers.");
    }

    private static SessionKind kind(String kind) {
        try {
            return SessionKind.valueOf(kind == null ? "" : kind.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw DomainException.of("kind", "Use learn or review.");
        }
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
