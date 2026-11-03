package com.bandup.api.vocabulary.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.application.ServiceUnavailableException;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.vocabulary.application.port.CardQueue;
import com.bandup.api.vocabulary.application.port.CardSummaries;
import com.bandup.api.vocabulary.application.port.WordCardAi;
import com.bandup.api.vocabulary.application.view.AdminCardViews.Card;
import com.bandup.api.vocabulary.application.view.AdminCardViews.CardPage;
import com.bandup.api.vocabulary.application.view.AdminCardViews.Gap;
import com.bandup.api.vocabulary.application.view.AdminCardViews.Meaning;
import com.bandup.api.vocabulary.application.view.AdminCardViews.Pair;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;
import com.bandup.api.vocabulary.domain.word.valueobject.GapTask;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.PairTask;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * The admin word card use cases (vocabulary requirement 8): list, get, edit
 * (every rule with its path; {@code version} for conflicts), regenerate with
 * the AI, and delete a card nobody saved. An edit is seen by every learner.
 */
@Service
@RequiredArgsConstructor
public class WordCardAdmin {

    private final WordCardRepository cards;
    private final CardSummaries summaries;
    private final WordCardAi ai;
    private final CardQueue queue;

    @Transactional(readOnly = true)
    public CardPage list(String status, String query, int page, int size) {
        String s = status(status);
        String q = query == null || query.isBlank() ? null : query.strip().toLowerCase(Locale.ROOT);
        int p = Math.max(0, page);
        int n = Math.min(Math.max(1, size), 200);
        return new CardPage(summaries.list(s, q, p, n), p, n, summaries.total(s, q));
    }

    @Transactional(readOnly = true)
    public Card get(long id) {
        WordCard card = load(id);
        return view(card, card.version(), summaries.saves(card.id()));
    }

    @Transactional
    public Card update(long id, Card in) {
        if (in == null || in.version() == null) {
            throw new InvalidRequestException("The version is required.",
                    List.of(new Violation("version", "Send the version you loaded.")));
        }
        WordCard card = cards.lock(new WordId(id)).orElseThrow(WordCardAdmin::notFound);
        if (in.version() != card.version()) {
            throw new ConflictException("This word was changed by someone else. Reload it and try again.");
        }
        List<Violation> shape = new ArrayList<>();
        String lemmaText = VocabularyRules.NORMALISER.normalise(in.lemma());
        if (!VocabularyRules.GATE.isWord(lemmaText)) {
            shape.add(new Violation("lemma", "Use 1 to 3 lower-case English words."));
        }
        Set<WordForm> forms = new LinkedHashSet<>();
        List<String> given = in.forms() == null ? List.of() : in.forms();
        for (int i = 0; i < given.size(); i++) {
            String form = VocabularyRules.NORMALISER.normalise(given.get(i));
            if (VocabularyRules.GATE.isWord(form)) {
                forms.add(new WordForm(form));
            } else {
                shape.add(new Violation("forms[" + i + "]", "Use 1 to 3 lower-case English words."));
            }
        }
        if (!shape.isEmpty()) {
            throw new DomainException(shape);
        }
        Lemma lemma = new Lemma(lemmaText);
        forms.add(lemma.asForm());
        card.edit(lemma, forms, content(in), VocabularyRules.POLICY);
        long version = cards.save(card);
        return view(card, version, summaries.saves(card.id()));
    }

    /** The AI writes the card again; learners keep their words and see the old content meanwhile. */
    @Transactional
    public Card regenerate(long id) {
        if (!ai.available()) {
            throw new ServiceUnavailableException("The AI isn't configured (BANDUP_OPENROUTER_API_KEY).");
        }
        WordCard card = cards.lock(new WordId(id)).orElseThrow(WordCardAdmin::notFound);
        card.retry();
        long version = cards.save(card);
        queue.writeAfterCommit(card.id());
        return view(card, version, summaries.saves(card.id()));
    }

    @Transactional
    public void delete(long id) {
        WordCard card = load(id);
        int saves = summaries.saves(card.id());
        if (saves > 0) {
            throw new ConflictException(saves + (saves == 1 ? " learner has" : " learners have")
                    + " saved this word; it can't be deleted.");
        }
        cards.delete(card);
    }

    private WordCard load(long id) {
        if (id < 1) {
            throw notFound();
        }
        return cards.findById(new WordId(id)).orElseThrow(WordCardAdmin::notFound);
    }

    private static CardContent content(Card in) {
        Meaning m = in.meaning() == null ? new Meaning("", "") : in.meaning();
        Gap g = in.gap() == null ? new Gap("", "") : in.gap();
        Pair p = in.pair() == null ? new Pair("", List.of(), -1) : in.pair();
        return new CardContent(text(in.pos()), text(in.level()), text(in.ipa()),
                new com.bandup.api.vocabulary.domain.word.valueobject.Meaning(text(m.en()), text(m.az())),
                texts(in.examples()), texts(in.collocations()), texts(in.family()),
                new GapTask(text(g.sentence()), text(g.answer())),
                new PairTask(text(p.sentence()), texts(p.options()), p.answer() == null ? -1 : p.answer()),
                texts(in.wrongMeanings()));
    }

    static Card view(WordCard card, long version, int saves) {
        CardContent c = card.content().orElse(null);
        return new Card(card.id().value(), version, card.status().name(), card.source().name(), card.revision(), saves,
                card.lastError().orElse(null), null, card.lemma().map(Lemma::value).orElse(null),
                card.forms().stream().map(WordForm::value).toList(), c == null ? null : c.pos(),
                c == null ? null : c.level(), c == null ? null : c.ipa(),
                c == null ? null : new Meaning(c.meaning().en(), c.meaning().az()), c == null ? null : c.examples(),
                c == null ? null : c.collocations(), c == null ? null : c.family(),
                c == null ? null : new Gap(c.gap().sentence(), c.gap().answer()),
                c == null ? null : new Pair(c.pair().sentence(), c.pair().options(), c.pair().answer()),
                c == null ? null : c.wrongMeanings());
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static List<String> texts(List<String> values) {
        return values == null ? List.of() : values.stream().map(WordCardAdmin::text).toList();
    }

    private static String status(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("all")) {
            return null;
        }
        String s = status.strip().toUpperCase(Locale.ROOT);
        if (!List.of("PREPARING", "READY", "FAILED", "REJECTED").contains(s)) {
            throw DomainException.of("status", "Use preparing, ready, failed, rejected or all.");
        }
        return s;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("No such word.");
    }
}
