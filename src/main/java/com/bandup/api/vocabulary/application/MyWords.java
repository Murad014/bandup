package com.bandup.api.vocabulary.application;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.application.ServiceUnavailableException;
import com.bandup.api.shared.application.TooManyRequestsException;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.application.port.AiUsage;
import com.bandup.api.vocabulary.application.port.CardQueue;
import com.bandup.api.vocabulary.application.port.LearnerWords;
import com.bandup.api.vocabulary.application.port.VocabularyDays;
import com.bandup.api.vocabulary.application.port.VocabularySettings;
import com.bandup.api.vocabulary.application.port.WordCardAi;
import com.bandup.api.vocabulary.application.view.VocabularyViews.SavedWordView;
import com.bandup.api.vocabulary.application.view.VocabularyViews.WordsPage;
import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.repository.SavedWordRepository;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.CardStatus;

/** The learner's own words (vocabulary requirement 4): the page, polling, remove, try again. */
@Service
@RequiredArgsConstructor
public class MyWords {

    public static final int MAX_PAGE = 100;

    private final Learner learner;
    private final LearnerWords words;
    private final SavedWordRepository saved;
    private final WordCardRepository cards;
    private final WordCardAi ai;
    private final AiUsage usage;
    private final VocabularySettings settings;
    private final VocabularyDays days;
    private final CardQueue queue;

    @Transactional(readOnly = true)
    public WordsPage page(String state, String query, String exercise, int page, int size) {
        LearnerId me = learner.required();
        String s = state(state);
        int p = Math.max(0, page);
        int n = Math.min(Math.max(1, size), MAX_PAGE);
        return new WordsPage(words.counts(me), words.due(me, days.today()), words.newReady(me),
                words.page(me, s, query, exercise, p, n), p, n, words.total(me, s, query, exercise));
    }

    /** The words with these ids (the page polls PREPARING words). */
    @Transactional(readOnly = true)
    public List<SavedWordView> byIds(Collection<UUID> ids) {
        if (ids.size() > MAX_PAGE) {
            throw DomainException.of("ids", "Ask for at most " + MAX_PAGE + " words.");
        }
        return words.byIds(learner.required(), ids);
    }

    /** Removes the learner's word; the shared card stays (requirement 4.4). */
    @Transactional
    public void remove(UUID id) {
        saved.delete(mine(id));
    }

    /** "Try again" on a word whose card FAILED; counts towards the daily AI limit. */
    @Transactional
    public void retry(UUID id) {
        LearnerId me = learner.required();
        SavedWord word = mine(id);
        WordCard card = cards.lock(word.word()).orElseThrow();
        if (card.status() != CardStatus.FAILED) {
            throw DomainException.of("status", "Only a word that couldn't be prepared can be tried again.");
        }
        if (!ai.available()) {
            throw new ServiceUnavailableException("New words can't be prepared right now. Please try again later.");
        }
        LocalDate today = days.today();
        if (usage.count(me, today) >= settings.dailyAiCards()) {
            throw new TooManyRequestsException("You can add more new words tomorrow.", Duration.ofHours(1));
        }
        card.retry();
        cards.save(card);
        usage.record(me, today);
        queue.writeAfterCommit(card.id());
    }

    private SavedWord mine(UUID id) {
        LearnerId me = learner.required();
        return saved.findById(new SavedWordId(id)).filter(w -> w.belongsTo(me))
                .orElseThrow(() -> new NotFoundException("No such word."));
    }

    private static String state(String state) {
        if (state == null || state.isBlank() || state.equalsIgnoreCase("all")) {
            return null;
        }
        String s = state.strip().toUpperCase(Locale.ROOT);
        if (!List.of("NEW", "LEARNING", "MASTERED").contains(s)) {
            throw DomainException.of("state", "Use new, learning, mastered or all.");
        }
        return s;
    }
}
