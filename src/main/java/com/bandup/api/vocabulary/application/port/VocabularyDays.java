package com.bandup.api.vocabulary.application.port;

import java.time.LocalDate;

/** Port: today in the vocabulary day zone. */
public interface VocabularyDays {

    LocalDate today();
}
