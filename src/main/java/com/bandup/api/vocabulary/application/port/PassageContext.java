package com.bandup.api.vocabulary.application.port;

import java.util.Optional;

/** Port: what a reading passage already says a dotted word means (Content's glosses, read with SQL). */
public interface PassageContext {

    /**
     * @param exerciseSlug the passage
     * @param form         the normalised word (matched to the gloss's surface or lemma)
     */
    Optional<Gloss> gloss(String exerciseSlug, String form);

    /** The passage's title, to show where a word came from. */
    Optional<String> title(String exerciseSlug);

    record Gloss(String en, String az) {
    }
}
