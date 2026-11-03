package com.bandup.api.vocabulary.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.vocabulary.application.port.WordCardAi;
import com.bandup.api.vocabulary.application.port.WordCardAi.CardAnswer;
import com.bandup.api.vocabulary.application.port.WordCardAi.CardRequest;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * Writes one PREPARING card with the AI (vocabulary requirement 3; design
 * §4). The AI call runs outside any transaction; each result is stored by
 * {@link CardResults} in a short one. An invalid answer is retried once with
 * the problems found; then the card is FAILED.
 */
@Service
@RequiredArgsConstructor
public class CardWriter {

    private final WordCardAi ai;
    private final CardResults results;

    public void write(WordId id) {
        Optional<CardRequest> job = results.job(id);
        if (job.isEmpty()) {
            return;
        }
        CardRequest request = job.get();
        while (true) {
            CardAnswer answer;
            try {
                answer = ai.write(request);
            } catch (WordCardAi.AiUnavailableException e) {
                results.failed(id, "The AI couldn't be reached: " + e.getMessage(), true);
                return;
            }
            List<String> problems = switch (answer) {
                case CardAnswer.NotAWord notAWord -> {
                    results.rejected(id, notAWord.reason());
                    yield List.of();
                }
                case CardAnswer.Unreadable unreadable -> List.of(unreadable.problem());
                case CardAnswer.Card card -> store(id, card);
            };
            if (problems.isEmpty() || !results.failed(id, String.join("; ", problems), false)) {
                return;
            }
            request = request.retry(problems);
        }
    }

    /** @return the problems found; empty when the card was stored */
    private List<String> store(WordId id, CardAnswer.Card card) {
        String lemma = VocabularyRules.NORMALISER.normalise(card.lemma());
        if (!VocabularyRules.GATE.isWord(lemma)) {
            return List.of("lemma: use 1 to 3 lower-case English words.");
        }
        Set<WordForm> forms = new LinkedHashSet<>();
        forms.add(new WordForm(lemma));
        for (String form : card.forms()) {
            String normalised = VocabularyRules.NORMALISER.normalise(form);
            if (VocabularyRules.GATE.isWord(normalised)) {
                forms.add(new WordForm(normalised));
            }
        }
        Lemma base = new Lemma(lemma);
        List<Violation> violations = VocabularyRules.POLICY.check(base, forms, card.content());
        if (!violations.isEmpty()) {
            List<String> problems = new ArrayList<>();
            violations.forEach(v -> problems.add(v.field() + ": " + v.message()));
            return problems;
        }
        results.ready(id, base, forms, card.content());
        return List.of();
    }
}
