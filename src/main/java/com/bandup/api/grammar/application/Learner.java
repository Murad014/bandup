package com.bandup.api.grammar.application;

import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonText;
import com.bandup.api.grammar.domain.progress.valueobject.CourseLevel;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.language.LanguageCode;

/** Who is calling and in which language, as the grammar use cases need it. */
@Component
@RequiredArgsConstructor
class Learner {

    private final CurrentUser currentUser;
    private final LanguageCatalog languages;

    /** The signed-in learner; empty for a guest. */
    Optional<LearnerId> current() {
        return currentUser.get().map(caller -> new LearnerId(caller.userId()));
    }

    LanguageChain language(String requested) {
        return languages.fallbacks().chainFor(requested);
    }

    /** A text map (language → text) in the chain's first language that has it. */
    static String pick(Map<String, String> texts, LanguageChain chain) {
        for (LanguageCode code : chain.codes()) {
            for (Map.Entry<String, String> entry : texts.entrySet()) {
                if (LanguageCode.of(entry.getKey()).equals(code)) {
                    return entry.getValue();
                }
            }
        }
        return "";
    }

    static String pick(LessonText text, LanguageChain chain) {
        return text.in(chain);
    }

    /** @throws DomainException (field {@code level}) for anything but A2, B1, B2, C1 */
    static GrammarLevel level(String code) {
        try {
            return GrammarLevel.valueOf(code);
        } catch (RuntimeException e) {
            throw DomainException.of("level", "Use A2, B1, B2 or C1.");
        }
    }

    static CourseLevel courseLevel(GrammarLevel level) {
        return CourseLevel.valueOf(level.name());
    }
}
