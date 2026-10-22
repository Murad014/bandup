package com.bandup.api.content.domain.exercise.valueobject;

import java.util.List;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * The keys found in a passage's sanitized HTML (by the {@code MarkupInspector}
 * port), in document order and exactly as written, so the aggregate can
 * reject invalid or repeated keys (backend requirement 5.4).
 *
 * @param glosses  every {@code span.w[data-g]}: its key and its text (the surface word)
 * @param evidence every {@code span.ev}'s id
 */
@ValueObject
public record MarkupKeys(List<GlossMark> glosses, List<String> evidence) {

    public MarkupKeys {
        glosses = List.copyOf(glosses);
        evidence = List.copyOf(evidence);
    }

    public static MarkupKeys none() {
        return new MarkupKeys(List.of(), List.of());
    }

    /** One glossed word as written in the HTML. */
    public record GlossMark(String key, String surface) {

        public GlossMark {
            Objects.requireNonNull(key, "key");
            surface = surface == null ? "" : surface.strip();
        }
    }
}
