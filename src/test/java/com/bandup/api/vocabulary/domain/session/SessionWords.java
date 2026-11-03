package com.bandup.api.vocabulary.domain.session;

import java.util.List;
import java.util.UUID;

import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionWord;

/** Word snapshots for the session tests. */
final class SessionWords {

    private SessionWords() {
    }

    static SessionWord word(String word, String meaning, int box) {
        return new SessionWord(new SavedWordId(UUID.randomUUID()), word, List.of(word, word + "s"), "noun", "B2",
                "/x/", meaning, word + "-az", List.of("Example one with " + word + ".", "Example two with " + word + "."),
                List.of("a " + word), List.of(), null, null, "We saw the ___ near the lake.", word,
                "A natural ___ in the park.", List.of(word, "other", "third", "fourth"), 0,
                List.of("wrong " + word + " one", "wrong " + word + " two", "wrong " + word + " three"), box);
    }

    static List<SessionWord> five() {
        return List.of(word("predator", "an animal that hunts others", 0), word("erosion", "slow damage of land", 0),
                word("abundant", "more than enough", 0), word("decline", "a gradual fall", 0),
                word("thrive", "to grow well", 0));
    }
}
