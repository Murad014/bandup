package com.bandup.api.vocabulary.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.application.port.LearnerWords;
import com.bandup.api.vocabulary.application.port.LearnerWords.Recallable;
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
 * learn session puts its words in box 1. A recall session (Azerbaijani →
 * English) is practice only: it never moves a word.
 */
@Service
@RequiredArgsConstructor
public class WordSessions {

    public static final int LEARN_WORDS = 5;
    public static final int REVIEW_WORDS = 20;
    public static final int RECALL_WORDS = 10;

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
        SessionId id = SessionId.random();
        Map<SavedWordId, List<String>> alsoAccepted = new HashMap<>();
        List<UUID> ids = switch (k) {
            case LEARN -> words.learnable(me, blank(exercise), LEARN_WORDS);
            case REVIEW -> words.due(me, days.today(), REVIEW_WORDS);
            case RECALL -> recallWords(me, id.seed(), alsoAccepted);
        };
        if (ids.isEmpty()) {
            throw DomainException.of("kind", switch (k) {
                case LEARN -> "There are no new words ready to learn.";
                case REVIEW -> "Nothing is due for review.";
                case RECALL -> "Learn some words first, then test yourself here.";
            });
        }
        List<SessionWord> snapshots = new ArrayList<>();
        for (UUID savedId : ids) {
            SavedWord word = saved.findById(new SavedWordId(savedId)).orElseThrow();
            cards.findById(word.word()).filter(WordCard::isReady).ifPresent(card -> snapshots.add(snapshot(word, card)));
        }
        WordSession session = WordSession.start(id, me, k, snapshots,
                VocabularyRules.TASKS.build(k, snapshots, id.seed(), alsoAccepted), clock.instant());
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

    /**
     * Up to 10 learned words at random (seeded by the session), one per Azerbaijani meaning; fills
     * {@code alsoAccepted} with the spellings of the learner's other words that share a word's meaning
     * (requirements 11.2, 11.4).
     */
    private List<UUID> recallWords(LearnerId me, long seed, Map<SavedWordId, List<String>> alsoAccepted) {
        List<Recallable> all = new ArrayList<>(words.recallable(me));
        Collections.shuffle(all, new Random(seed));
        Map<String, List<Recallable>> byMeaning = new LinkedHashMap<>();
        all.forEach(r -> byMeaning.computeIfAbsent(meaningKey(r.meaningAz()), m -> new ArrayList<>()).add(r));
        List<UUID> chosen = new ArrayList<>();
        for (List<Recallable> same : byMeaning.values()) {
            if (chosen.size() == RECALL_WORDS) {
                break;
            }
            Recallable first = same.get(0);
            chosen.add(first.saved());
            List<String> others = same.stream().skip(1).flatMap(r -> r.spellings().stream()).toList();
            if (!others.isEmpty()) {
                alsoAccepted.put(new SavedWordId(first.saved()), others);
            }
        }
        return chosen;
    }

    private static String meaningKey(String az) {
        return az == null ? "" : az.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
            case Task.Translate t -> new TaskView(id, t.type(), phase, null, null, null,
                    t.hint().isEmpty() ? null : t.hint(), null, t.az(), null,
                    null, null, from, box);
        };
    }

    /** JSON → answer: a number (option), a string (typed), a list of numbers (match) or null ("I don't remember"). */
    private static GivenAnswer given(Object answer) {
        if (answer == null) {
            return new GivenAnswer.DontKnow();
        }
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
            throw DomainException.of("kind", "Use learn, review or recall.");
        }
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
