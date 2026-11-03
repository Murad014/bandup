package com.bandup.api.vocabulary.application.port;

import java.time.Instant;
import java.util.List;

import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/** Port: the admin list of word cards (SQL). */
public interface CardSummaries {

    /** @param status PREPARING, READY, FAILED, REJECTED or null; @param query part of a form, or null */
    List<CardSummary> list(String status, String query, int page, int size);

    long total(String status, String query);

    /** How many learners saved the card. */
    int saves(WordId card);

    /** One row: {@code word} is the lemma or, while there is none, the asked form. */
    record CardSummary(long id, String word, String level, String status, String source, int saves, String lastError,
            Instant updatedAt) {
    }
}
