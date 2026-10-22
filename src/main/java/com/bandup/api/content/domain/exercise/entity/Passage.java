package com.bandup.api.content.domain.exercise.entity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.bandup.api.content.domain.exercise.valueobject.EvidenceKey;
import com.bandup.api.content.domain.exercise.valueobject.GlossKey;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;
import com.bandup.api.shared.domain.ddd.Entity;

/**
 * The reading passage of an exercise (one per exercise, so identified by
 * it): sanitized HTML plus the glosses and evidence spans marked in it.
 * Immutable; built by the {@code Exercise}, which keeps glosses and evidence
 * in sync with the HTML.
 */
@Entity
public final class Passage {

    private final SanitizedHtml html;
    private final List<Gloss> glosses;
    private final Set<EvidenceKey> evidence;

    public Passage(SanitizedHtml html, List<Gloss> glosses, Set<EvidenceKey> evidence) {
        this.html = Objects.requireNonNull(html, "html");
        this.glosses = List.copyOf(glosses);
        this.evidence = Collections.unmodifiableSet(new LinkedHashSet<>(evidence));
    }

    public SanitizedHtml html() {
        return html;
    }

    public int wordCount() {
        return html.wordCount();
    }

    /** In the order they appear in the passage. */
    public List<Gloss> glosses() {
        return glosses;
    }

    public Optional<Gloss> gloss(GlossKey key) {
        return glosses.stream().filter(g -> g.key().equals(key)).findFirst();
    }

    /** In the order they appear in the passage. */
    public Set<EvidenceKey> evidence() {
        return evidence;
    }

    @Override
    public String toString() {
        return "Passage[" + wordCount() + " words, " + glosses.size() + " glosses, evidence " + evidence + "]";
    }
}
