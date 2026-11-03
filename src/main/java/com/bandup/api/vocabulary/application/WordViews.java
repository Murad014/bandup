package com.bandup.api.vocabulary.application;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.bandup.api.vocabulary.application.port.PassageContext;
import com.bandup.api.vocabulary.application.view.VocabularyViews.CardView;
import com.bandup.api.vocabulary.application.view.VocabularyViews.MeaningView;
import com.bandup.api.vocabulary.application.view.VocabularyViews.SavedWordView;
import com.bandup.api.vocabulary.application.view.VocabularyViews.SourceView;
import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.valueobject.Box;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;

/** Builds the learner views of saved words and cards. */
@Component
@RequiredArgsConstructor
class WordViews {

    private final PassageContext passages;

    SavedWordView saved(SavedWord word, WordCard card) {
        CardContent c = card.content().orElse(null);
        String slug = word.source().exerciseSlug().orElse(null);
        return new SavedWordView(word.id().value(), card.display(),
                card.forms().stream().map(f -> f.value()).toList(), c == null ? null : c.pos(),
                c == null ? null : c.level(), c == null ? null : c.ipa(),
                c == null ? null : new MeaningView(c.meaning().en(), c.meaning().az()),
                new SourceView(slug, slug == null ? null : passages.title(slug).orElse(null),
                        word.source().sentenceText().orElse(null)),
                word.addedOn(), word.state().name(), word.box().map(Box::value).orElse(null), word.dueOn().orElse(null),
                card.status().name());
    }

    static CardView card(WordCard card) {
        CardContent c = card.content().orElseThrow();
        return new CardView(card.display(), c.pos(), c.level(), c.ipa(), new MeaningView(c.meaning().en(), c.meaning().az()),
                c.examples(), c.collocations(), c.family());
    }
}
