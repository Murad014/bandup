package com.bandup.api.vocabulary.application;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.ServiceUnavailableException;
import com.bandup.api.shared.application.TooManyRequestsException;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.application.port.AiUsage;
import com.bandup.api.vocabulary.application.port.CardQueue;
import com.bandup.api.vocabulary.application.port.VocabularyDays;
import com.bandup.api.vocabulary.application.port.VocabularySettings;
import com.bandup.api.vocabulary.application.port.WordCardAi;
import com.bandup.api.vocabulary.application.view.VocabularyViews.SaveResult;
import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.repository.SavedWordRepository;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSource;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.CardStatus;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

/**
 * Saves a word for the signed-in learner (vocabulary requirements 1–3). The
 * database is always asked first: a word or form some card already has is
 * saved at once with no AI call. Only a word no card has creates a PREPARING
 * card (one per spelling, a database rule) and one AI call after commit.
 */
@Service
@RequiredArgsConstructor
public class SaveWord {

    private static final int TRIES = 3;

    private final Learner learner;
    private final WordCardRepository cards;
    private final SavedWordRepository saved;
    private final WordCardAi ai;
    private final AiUsage usage;
    private final VocabularySettings settings;
    private final VocabularyDays days;
    private final CardQueue queue;
    private final WordViews views;

    /**
     * @param exercise the passage it was saved from (slug); may be null
     * @param sentence the sentence it was in; may be null
     * @throws DomainException 422 for junk or a word known not to be English
     * @throws TooManyRequestsException over the daily AI limit (known words never count)
     * @throws ServiceUnavailableException when a new word needs the AI and none is configured
     */
    @Transactional
    public SaveResult save(String text, String exercise, String sentence) {
        LearnerId me = learner.required();
        WordForm form = VocabularyRules.GATE.admit(text);
        WordSource source = new WordSource(exercise, sentence);
        LocalDate today = days.today();
        for (int attempt = 0; attempt < TRIES; attempt++) {
            Optional<WordCard> known = cards.findByForm(form);
            if (known.isPresent()) {
                return keep(me, known.get(), source, today);
            }
            if (!ai.available()) {
                throw new ServiceUnavailableException("New words can't be prepared right now. Please try again later.");
            }
            if (usage.count(me, today) >= settings.dailyAiCards()) {
                throw new TooManyRequestsException("You can add more new words tomorrow.", Duration.ofHours(1));
            }
            WordCard fresh = WordCard.preparing(cards.nextId(), form);
            if (cards.addIfFormFree(fresh)) {
                usage.record(me, today);
                SavedWord word = SavedWord.save(me, fresh.id(), source, today);
                saved.save(word);
                queue.writeAfterCommit(fresh.id());
                return new SaveResult(views.saved(word, fresh), false);
            }
            // Another learner created this card a moment ago: look again.
        }
        throw new ConflictException("Please try again.");
    }

    private SaveResult keep(LearnerId me, WordCard card, WordSource source, LocalDate today) {
        if (card.status() == CardStatus.REJECTED) {
            throw DomainException.of("text", "“" + card.display() + "” isn't an English word we can teach.");
        }
        Optional<SavedWord> mine = saved.find(me, card.id());
        if (mine.isPresent()) {
            return new SaveResult(views.saved(mine.get(), card), true);
        }
        SavedWord word = SavedWord.save(me, card.id(), source, today);
        saved.save(word);
        return new SaveResult(views.saved(word, card), false);
    }
}
