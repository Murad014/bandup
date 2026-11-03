package com.bandup.api.vocabulary.application.port;

import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * Port: asks for a card to be written in the background once the current
 * transaction has committed (so the writer sees the PREPARING card).
 */
public interface CardQueue {

    void writeAfterCommit(WordId card);
}
