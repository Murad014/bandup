package com.bandup.api.vocabulary.domain.word.aggregate;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.shared.domain.ddd.AggregateRoot;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.vocabulary.domain.word.service.CardPolicy;
import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;
import com.bandup.api.vocabulary.domain.word.valueobject.CardSource;
import com.bandup.api.vocabulary.domain.word.valueobject.CardStatus;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * One shared word card (aggregate root; vocabulary requirement 3): the word's
 * base form, its other forms and what it teaches. Every learner who saves any
 * of its forms gets this card; the AI writes it once.
 *
 * <ul>
 *   <li>{@link #preparing} — a new card that owns just the asked form, while the AI writes it;
 *   <li>{@link #ready} — the AI (or an admin) gave valid content;
 *   <li>{@link #failed} — an invalid AI answer; after {@link #MAX_ATTEMPTS} it is FAILED;
 *   <li>{@link #rejected} — not a word we teach; the form stays so it never calls the AI again;
 *   <li>{@link #retry} — FAILED (learner) or READY (admin regenerate) → PREPARING;
 *   <li>{@link #edit} — an admin writes the content (any status but PREPARING).
 * </ul>
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class WordCard {

    /** AI answers per preparation before the card is FAILED (the first try and one retry). */
    public static final int MAX_ATTEMPTS = 2;

    @EqualsAndHashCode.Include
    @Getter
    private final WordId id;
    private Lemma lemma;
    private final Set<WordForm> forms;
    @Getter
    private CardStatus status;
    private CardContent content;
    @Getter
    private CardSource source;
    @Getter
    private int revision;
    @Getter
    private int attempts;
    private String lastError;
    @Getter
    private final long version;

    private WordCard(WordId id, Lemma lemma, Collection<WordForm> forms, CardStatus status, CardContent content,
            CardSource source, int revision, int attempts, String lastError, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.lemma = lemma;
        this.forms = new LinkedHashSet<>(forms);
        this.status = Objects.requireNonNull(status, "status");
        this.content = content;
        this.source = Objects.requireNonNull(source, "source");
        this.revision = revision;
        this.attempts = attempts;
        this.lastError = lastError;
        this.version = version;
    }

    /** A new card for a form no card has yet; the AI is asked to write it. */
    public static WordCard preparing(WordId id, WordForm asked) {
        return new WordCard(id, null, List.of(asked), CardStatus.PREPARING, null, CardSource.AI, 0, 0, null, -1);
    }

    public static WordCard restore(WordId id, Lemma lemma, Collection<WordForm> forms, CardStatus status,
            CardContent content, CardSource source, int revision, int attempts, String lastError, long version) {
        return new WordCard(id, lemma, forms, status, content, source, revision, attempts, lastError, version);
    }

    /**
     * The AI's card: the base form, its forms (the forms this card already
     * owns are kept) and the content. Every rule must pass.
     *
     * @throws DomainException with every broken rule
     */
    public void ready(Lemma newLemma, Collection<WordForm> newForms, CardContent newContent, CardPolicy policy) {
        if (status != CardStatus.PREPARING && status != CardStatus.FAILED) {
            throw new IllegalStateException("Only a preparing or failed card gets AI content, not " + status);
        }
        Set<WordForm> all = new LinkedHashSet<>(newForms);
        all.addAll(forms);
        apply(newLemma, all, newContent, policy);
        source = CardSource.AI;
    }

    /** An invalid or missing AI answer; the card stays PREPARING until it ran out of attempts. */
    public void failed(String error) {
        if (status != CardStatus.PREPARING) {
            throw new IllegalStateException("Only a preparing card can fail, not " + status);
        }
        attempts++;
        lastError = error;
        if (attempts >= MAX_ATTEMPTS) {
            status = CardStatus.FAILED;
        }
    }

    public boolean canTryAgain() {
        return status == CardStatus.PREPARING && attempts < MAX_ATTEMPTS;
    }

    /** Not an English word we teach (vocabulary requirement 3.6). */
    public void rejected(String reason) {
        if (status != CardStatus.PREPARING) {
            throw new IllegalStateException("Only a preparing card can be rejected, not " + status);
        }
        status = CardStatus.REJECTED;
        lastError = reason;
        content = null;
    }

    /** "Try again" on a FAILED card, or an admin's "Regenerate" on a READY one; the content is kept meanwhile. */
    public void retry() {
        if (status != CardStatus.FAILED && status != CardStatus.READY) {
            throw DomainException.of("status", "Only a ready or failed card can be written again.");
        }
        status = CardStatus.PREPARING;
        attempts = 0;
        lastError = null;
    }

    /** An admin writes the card (vocabulary requirement 8.2); any status but PREPARING. */
    public void edit(Lemma newLemma, Collection<WordForm> newForms, CardContent newContent, CardPolicy policy) {
        if (status == CardStatus.PREPARING) {
            throw DomainException.of("status", "The AI is writing this card; wait until it is done.");
        }
        apply(newLemma, new LinkedHashSet<>(newForms), newContent, policy);
        source = CardSource.ADMIN;
    }

    /** Another form of the same word joins this card (e.g. when the AI's lemma already has a card). */
    public void addForms(Collection<WordForm> more) {
        forms.addAll(more);
    }

    private void apply(Lemma newLemma, Set<WordForm> newForms, CardContent newContent, CardPolicy policy) {
        Objects.requireNonNull(newLemma, "lemma");
        Objects.requireNonNull(newContent, "content");
        List<Violation> problems = policy.check(newLemma, newForms, newContent);
        if (!problems.isEmpty()) {
            throw new DomainException(problems);
        }
        lemma = newLemma;
        forms.clear();
        forms.addAll(newForms);
        content = newContent;
        status = CardStatus.READY;
        revision++;
        attempts = 0;
        lastError = null;
    }

    public boolean isReady() {
        return status == CardStatus.READY;
    }

    public Optional<Lemma> lemma() {
        return Optional.ofNullable(lemma);
    }

    public Optional<CardContent> content() {
        return Optional.ofNullable(content);
    }

    public Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    public Set<WordForm> forms() {
        return Collections.unmodifiableSet(forms);
    }

    /** The word to show: the lemma, or the asked form while there is none. */
    public String display() {
        return lemma != null ? lemma.value() : forms.iterator().next().value();
    }
}
