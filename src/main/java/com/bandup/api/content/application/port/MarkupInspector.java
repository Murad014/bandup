package com.bandup.api.content.application.port;

import com.bandup.api.content.domain.exercise.valueobject.MarkupKeys;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;

/**
 * Port: reads the gloss and evidence keys of a sanitized passage, in
 * document order and exactly as written (repeats included), for
 * {@code Exercise} to sync and validate (backend requirements 5.2–5.4).
 */
public interface MarkupInspector {

    MarkupKeys inspect(SanitizedHtml passage);
}
