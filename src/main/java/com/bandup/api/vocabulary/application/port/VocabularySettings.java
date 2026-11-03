package com.bandup.api.vocabulary.application.port;

/** Port: the vocabulary settings the use cases need. */
public interface VocabularySettings {

    /** New AI cards one learner may cause per day (vocabulary requirement 3.8). */
    int dailyAiCards();
}
