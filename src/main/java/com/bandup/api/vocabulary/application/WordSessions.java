package com.bandup.api.vocabulary.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.application.port.LearnerWords;
import com.bandup.api.vocabulary.application.port.LearnerWords.Learned;
import com.bandup.api.vocabulary.application.port.PassageContext;
import com.bandup.api.vocabulary.application.view.SessionViews.AnswerView;
import com.bandup.api.vocabulary.application.view.SessionViews.MeetWord;
import com.bandup.api.vocabulary.application.view.SessionViews.RoundView;
import com.bandup.api.vocabulary.application.view.SessionViews.SessionView;
import com.bandup.api.vocabulary.application.view.SessionViews.StepView;
import com.bandup.api.vocabulary.application.view.SessionViews.TaskView;
import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.repository.SavedWordRepository;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.saved.valueobject.SetMember;
import com.bandup.api.vocabulary.domain.saved.valueobject.SetStep;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSet;
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
 * Learn sessions and rounds (vocabulary requirements 5–7). Words are
 * snapshotted when the session starts; every answer is graded on the server.
 * A finished learn session puts its words in the sets; in a round, the first
 * try of each word moves it (right once → mastered, a miss → back to 0).
 */
@Service
@RequiredArgsConstructor
public class WordSessions {

    public static final int LEARN_WORDS = 5;

    private final Learner learner;
    private final LearnerWords words;
    private final SavedWordRepository saved;
    private final WordCardRepository cards;
    private final WordSessionRepository sessions;
    private final PassageContext passages;
    private final Clock clock;

    @Transactional
    public SessionView start(String kind, String exercise) {
        LearnerId me = learner.required();
        SessionKind k = kind(kind);
        SessionId id = SessionId.random();
        Map<SavedWordId, List<String>> alsoAccepted = new HashMap<>();
        RoundView round = null;
        List<UUID> ids;
        if (k == SessionKind.LEARN) {
            ids = words.learnable(me, blank(exercise), LEARN_WORDS);
            if (ids.isEmpty()) {
                throw DomainException.of("kind", "There are no new words ready to learn.");
            }
        } else {
            List<Learned> learned = words.learned(me);
            List<WordSet> sets = VocabularyRules.SETS.split(learned.stream().map(WordSessions::member).toList());
            WordSet set = VocabularyRules.SETS.current(sets).orElseThrow(() -> DomainException.of("kind",
                    learned.isEmpty() ? "Learn some words first — they join Set 1."
                            : "Every set is mastered. Learn new words to start the next set."));
            ids = roundWords(set, learned, id.seed(), alsoAccepted);
            if (ids.isEmpty()) {
                throw DomainException.of("kind", "The words of this set are still being prepared. Try again soon.");
            }
            round = new RoundView(set.number(), sets.size(), set.rounds() + 1);
        }
        List<SessionWord> snapshots = new ArrayList<>();
        for (UUID savedId : ids) {
            SavedWord word = saved.findById(new SavedWordId(savedId)).orElseThrow();
            cards.findById(word.word()).filter(WordCard::isReady).ifPresent(card -> snapshots.add(snapshot(word, card)));
        }
        WordSession session = WordSession.start(id, me, k, snapshots,
                VocabularyRules.TASKS.build(k, snapshots, id.seed(), alsoAccepted), clock.instant());
        sessions.save(session);
        return view(session, round);
    }

    /** A session again; {@code set} is only in the start response. */
    @Transactional
    public SessionView get(UUID sessionId) {
        return view(mine(sessionId), null);
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
        StepView step = null;
        if (session.kind() == SessionKind.ROUND && f.firstTry() && f.word() != null) {
            step = saved.findById(f.word()).map(w -> step(w, f.correct())).orElse(null);
        }
        if (session.kind() == SessionKind.LEARN && f.finished()) {
            for (SessionWord w : session.words()) {
                saved.findById(w.saved()).ifPresent(word -> {
                    word.learned(clock.instant());
                    saved.save(word);
                });
            }
        }
        sessions.save(session);
        return new AnswerView(f.correct(), f.expected(), f.why(), f.explanation(), f.mastered(), f.total(),
                f.finished(), step);
    }

    /**
     * The set's words to ask (not mastered, READY), shuffled with the session's seed; fills {@code alsoAccepted}
     * with the spellings of the learner's other learned words that share a word's Azerbaijani meaning
     * (requirements 6.4, 6.5).
     */
    private static List<UUID> roundWords(WordSet set, List<Learned> learned, long seed,
            Map<SavedWordId, List<String>> alsoAccepted) {
        List<UUID> ask = new ArrayList<>(set.toAsk().stream().map(m -> m.saved().value()).toList());
        Collections.shuffle(ask, new Random(seed));
        Map<String, List<Learned>> byMeaning = new HashMap<>();
        learned.stream().filter(Learned::ready)
                .forEach(l -> byMeaning.computeIfAbsent(meaningKey(l.meaningAz()), m -> new ArrayList<>()).add(l));
        for (Learned l : learned) {
            if (!ask.contains(l.saved())) {
                continue;
            }
            List<String> others = byMeaning.getOrDefault(meaningKey(l.meaningAz()), List.of()).stream()
                    .filter(o -> !o.saved().equals(l.saved())).flatMap(o -> o.spellings().stream()).toList();
            if (!others.isEmpty()) {
                alsoAccepted.put(new SavedWordId(l.saved()), others);
            }
        }
        return ask;
    }

    static SetMember member(Learned l) {
        return new SetMember(new SavedWordId(l.saved()), l.streak(), l.mastered(), l.rounds(), l.ready());
    }

    private static String meaningKey(String az) {
        return az == null ? "" : az.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private StepView step(SavedWord word, boolean correct) {
        SetStep s = word.roundAnswer(correct);
        saved.save(word);
        return new StepView(s.streak(), s.mastered(), s.changed());
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
                c.pair().sentence(), c.pair().options(), c.pair().answer(), c.wrongMeanings());
    }

    private static SessionView view(WordSession s, RoundView round) {
        boolean learn = s.kind() == SessionKind.LEARN;
        List<MeetWord> meet = learn ? s.words().stream().map(w -> new MeetWord(w.word(), w.pos(), w.level(), w.ipa(),
                w.meaningEn(), w.meaningAz(), w.examples(), w.collocations(), w.family(), w.sentence(), w.from())).toList()
                : List.of();
        List<TaskView> tasks = s.tasks().stream().map(WordSessions::task).toList();
        return new SessionView(s.id().value(), s.kind().name(), meet, tasks,
                s.queue().stream().map(TaskId::value).toList(), s.mastered().size(), s.masterTotal(), s.isFinished(),
                round);
    }

    private static TaskView task(Task task) {
        String id = task.id().value();
        String phase = task.phase().name();
        return switch (task) {
            case Task.ChooseMeaning t -> new TaskView(id, t.type(), phase, t.term(), t.options(), null, null, null, null,
                    null, null, null);
            case Task.Match t -> new TaskView(id, t.type(), phase, null, null, null, null, null, null, null, t.terms(),
                    t.meanings());
            case Task.Gap t -> new TaskView(id, t.type(), phase, null, null, t.sentence(), t.hint(), null, null, null,
                    null, null);
            case Task.TypeFromMeaning t -> new TaskView(id, t.type(), phase, null, null, null, t.hint(), t.meaning(),
                    null, null, null, null);
            case Task.Listen t -> new TaskView(id, t.type(), phase, null, null, null, null, null, null, t.speak(), null,
                    null);
            case Task.Pair t -> new TaskView(id, t.type(), phase, null, t.options(), t.sentence(), null, null, null,
                    null, null, null);
            case Task.Translate t -> new TaskView(id, t.type(), phase, null, null, null,
                    t.hint().isEmpty() ? null : t.hint(), null, t.az(), null,
                    null, null);
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
            throw DomainException.of("kind", "Use learn or round.");
        }
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
