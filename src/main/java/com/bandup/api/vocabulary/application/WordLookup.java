package com.bandup.api.vocabulary.application;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.vocabulary.application.view.VocabularyViews.CardView;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;

/** A word's card if the bank has it READY (public; never calls the AI). */
@Service
@RequiredArgsConstructor
public class WordLookup {

    private final WordCardRepository cards;

    @Transactional(readOnly = true)
    public CardView lookup(String text) {
        return cards.findByForm(VocabularyRules.GATE.admit(text)).filter(WordCard::isReady).map(WordViews::card)
                .orElseThrow(() -> new NotFoundException("This word isn't in Orbiko's word bank yet."));
    }
}
