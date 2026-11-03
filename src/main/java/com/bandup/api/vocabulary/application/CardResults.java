package com.bandup.api.vocabulary.application;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.vocabulary.application.port.PassageContext;
import com.bandup.api.vocabulary.application.port.WordCardAi.CardRequest;
import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.repository.SavedWordRepository;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;
import com.bandup.api.vocabulary.domain.word.valueobject.CardStatus;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * Stores what the AI answered for a card, each in a short transaction with
 * the card locked (design §4). When the AI's base form already belongs to a
 * READY card, the new card joins it: its learners' words point there and the
 * new card is removed, so every word has one card.
 */
@Service
@RequiredArgsConstructor
class CardResults {

    private final WordCardRepository cards;
    private final SavedWordRepository saved;
    private final PassageContext passages;

    /** What to ask the AI for this card; empty when it isn't PREPARING any more. */
    @Transactional(readOnly = true)
    Optional<CardRequest> job(WordId id) {
        Optional<WordCard> card = cards.findById(id).filter(c -> c.status() == CardStatus.PREPARING);
        if (card.isEmpty()) {
            return Optional.empty();
        }
        String word = card.get().forms().iterator().next().value();
        Optional<SavedWord> context = saved.findByWord(id).stream()
                .filter(s -> s.source().sentenceText().isPresent())
                .min(Comparator.comparing(SavedWord::addedOn));
        String sentence = context.flatMap(s -> s.source().sentenceText()).orElse(null);
        Optional<PassageContext.Gloss> gloss = context.flatMap(s -> s.source().exerciseSlug())
                .flatMap(slug -> passages.gloss(slug, word));
        return Optional.of(new CardRequest(word, sentence, gloss.map(PassageContext.Gloss::en).orElse(null),
                gloss.map(PassageContext.Gloss::az).orElse(null), List.of()));
    }

    /** Not a word: the card keeps its form (no AI call ever again) and the learners' words are removed. */
    @Transactional
    void rejected(WordId id, String reason) {
        cards.lock(id).filter(c -> c.status() == CardStatus.PREPARING).ifPresent(card -> {
            card.rejected(reason == null || reason.isBlank() ? "not a word" : reason);
            cards.save(card);
            saved.findByWord(id).forEach(saved::delete);
        });
    }

    /**
     * An answer that wasn't a valid card.
     *
     * @param giveUp true to make the card FAILED at once (the AI can't be reached)
     * @return whether the AI may be asked again
     */
    @Transactional
    boolean failed(WordId id, String error, boolean giveUp) {
        Optional<WordCard> locked = cards.lock(id).filter(c -> c.status() == CardStatus.PREPARING);
        if (locked.isEmpty()) {
            return false;
        }
        WordCard card = locked.get();
        do {
            card.failed(error.length() > 500 ? error.substring(0, 500) : error);
        } while (giveUp && card.canTryAgain());
        cards.save(card);
        return card.canTryAgain();
    }

    /** A valid card: READY, or joined to the READY card that owns its base form. */
    @Transactional
    void ready(WordId id, Lemma lemma, Set<WordForm> forms, CardContent content) {
        Optional<WordCard> locked = cards.lock(id).filter(c -> c.status() == CardStatus.PREPARING);
        if (locked.isEmpty()) {
            return;
        }
        WordCard card = locked.get();
        Set<WordForm> all = new LinkedHashSet<>(forms);
        all.addAll(card.forms());
        List<WordCard> others = cards.findByForms(all).stream().filter(c -> !c.equals(card)).toList();
        Optional<WordCard> home = others.stream().filter(WordCard::isReady)
                .min(Comparator.comparing((WordCard c) -> !c.forms().contains(lemma.asForm())));
        if (home.isPresent()) {
            join(card, home.get(), all, others);
            return;
        }
        if (others.stream().anyMatch(c -> c.forms().contains(lemma.asForm()))) {
            return; // the base form is being written for another card; the retry job joins them later
        }
        card.ready(lemma, free(all, others), content, VocabularyRules.POLICY);
        cards.save(card);
    }

    private void join(WordCard card, WordCard home, Set<WordForm> forms, List<WordCard> others) {
        for (SavedWord word : saved.findByWord(card.id())) {
            if (saved.find(word.learner(), home.id()).isPresent()) {
                saved.delete(word);
            } else {
                word.pointTo(home.id());
                saved.save(word);
            }
        }
        Set<WordForm> moving = new LinkedHashSet<>(card.forms());
        moving.addAll(free(forms, others.stream().filter(c -> !c.equals(home)).toList()));
        cards.delete(card);
        WordCard target = cards.lock(home.id()).orElseThrow();
        target.addForms(moving);
        cards.save(target);
    }

    /** The forms no other card owns. */
    private static Set<WordForm> free(Set<WordForm> forms, List<WordCard> others) {
        Set<WordForm> free = new LinkedHashSet<>(forms);
        others.forEach(c -> free.removeAll(c.forms()));
        return free;
    }
}
