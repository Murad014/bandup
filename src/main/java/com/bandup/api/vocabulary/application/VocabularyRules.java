package com.bandup.api.vocabulary.application;

import lombok.experimental.UtilityClass;

import com.bandup.api.vocabulary.domain.session.service.AnswerNormaliser;
import com.bandup.api.vocabulary.domain.session.service.TaskBuilder;
import com.bandup.api.vocabulary.domain.word.service.CardPolicy;
import com.bandup.api.vocabulary.domain.word.service.WordGate;
import com.bandup.api.vocabulary.domain.word.service.WordNormaliser;

/** The vocabulary domain services the use cases share (plain objects, no state). */
@UtilityClass
final class VocabularyRules {

    static final WordNormaliser NORMALISER = new WordNormaliser();
    static final WordGate GATE = new WordGate(NORMALISER);
    static final CardPolicy POLICY = new CardPolicy(NORMALISER);
    static final AnswerNormaliser ANSWERS = new AnswerNormaliser();
    static final TaskBuilder TASKS = new TaskBuilder();
}
