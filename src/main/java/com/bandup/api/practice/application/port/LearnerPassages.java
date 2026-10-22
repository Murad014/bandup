package com.bandup.api.practice.application.port;

import java.util.Optional;

import com.bandup.api.practice.application.PassageView;
import com.bandup.api.shared.domain.language.LanguageChain;

/**
 * Port: the learner's view of a PUBLISHED reading exercise (backend
 * requirement 2). The adapter never reads answers, explanations or evidence
 * links, so they can't leak before submit.
 */
public interface LearnerPassages {

    /**
     * @param help the help language and its fallbacks; every piece of help text
     *             (gloss meanings, translated instructions, stage title) is
     *             resolved on its own along this chain
     * @return empty if the exercise doesn't exist, isn't PUBLISHED or isn't READING
     */
    Optional<PassageView> find(String slug, LanguageChain help);
}
