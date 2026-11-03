package com.bandup.api.vocabulary.application.port;

import java.util.List;

import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;

/**
 * Port: an AI that writes one word card (card-format.md). Called only for a
 * word no card has yet, never inside a transaction (it takes seconds).
 */
public interface WordCardAi {

    /** Whether the AI is configured (an API key is set). */
    boolean available();

    /**
     * @throws AiUnavailableException when the AI can't be reached or refuses the request
     */
    CardAnswer write(CardRequest request);

    /**
     * @param word          the normalised word asked for
     * @param sentence      the sentence the learner saved it from; may be null
     * @param passageEn     the passage's own meaning of the word (dotted words); may be null
     * @param passageAz     the same in Azerbaijani; may be null
     * @param problems      what was wrong with the previous answer (a retry); empty the first time
     */
    record CardRequest(String word, String sentence, String passageEn, String passageAz, List<String> problems) {

        public CardRequest {
            problems = List.copyOf(problems);
        }

        public CardRequest retry(List<String> found) {
            return new CardRequest(word, sentence, passageEn, passageAz, found);
        }
    }

    /** What the AI answered. */
    sealed interface CardAnswer {

        /** A card (still to be checked by {@code CardPolicy}). */
        record Card(String lemma, List<String> forms, CardContent content) implements CardAnswer {

            public Card {
                forms = List.copyOf(forms);
            }
        }

        /** Not an English word we teach (a name, rude, another language, a typo, a sentence). */
        record NotAWord(String reason) implements CardAnswer {
        }

        /** Something that isn't a card at all (not JSON, wrong shape). */
        record Unreadable(String problem) implements CardAnswer {
        }
    }

    class AiUnavailableException extends RuntimeException {

        public AiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
