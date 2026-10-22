package com.bandup.api.content.application.port;

import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;

/**
 * Port: cleans author HTML against the allow-list (backend requirement 5.1,
 * {@code reading-practice-ui} design §9 with {@code data-g}) before anything
 * is stored. The only way the application gets a {@link SanitizedHtml}.
 */
public interface HtmlSanitizer {

    /**
     * A reading passage: keeps the reading aids, {@code span.w[data-g]} (glossed
     * word) and {@code span.ev[id]} (evidence). Their key values are kept as
     * written, so the domain can report invalid ones to the author.
     */
    SanitizedHtml passage(String html);

    /** An instruction or explanation: formatting only, no ids or gloss keys. */
    SanitizedHtml text(String html);
}
