package com.bandup.api.vocabulary.application;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.application.UnauthenticatedException;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;

/** The signed-in learner, as the vocabulary use cases need it. */
@Component("vocabularyLearner")
@RequiredArgsConstructor
class Learner {

    private final CurrentUser currentUser;

    LearnerId required() {
        return currentUser.get().map(caller -> new LearnerId(caller.userId()))
                .orElseThrow(() -> new UnauthenticatedException("Sign in to save and learn words."));
    }
}
