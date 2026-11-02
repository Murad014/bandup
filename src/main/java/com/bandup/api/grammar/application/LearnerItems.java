package com.bandup.api.grammar.application;

import java.util.List;

import lombok.experimental.UtilityClass;

import com.bandup.api.grammar.application.view.LearnerViews.ItemView;
import com.bandup.api.grammar.domain.lesson.service.AnswerNormaliser;
import com.bandup.api.grammar.domain.lesson.service.TileShuffler;
import com.bandup.api.grammar.domain.lesson.valueobject.GivenAnswer;
import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.language.LanguageChain;

/** Items as learners see them (no keys, tiles shuffled) and answers as learners send them. */
@UtilityClass
final class LearnerItems {

    static final AnswerNormaliser NORMALISER = new AnswerNormaliser();
    private static final TileShuffler SHUFFLER = new TileShuffler(NORMALISER);

    static ItemView view(Item item, LanguageChain chain, long seed) {
        String id = item.id().value();
        String prompt = item.prompt().in(chain);
        return switch (item) {
            case Item.Choose c -> new ItemView(id, c.code(), prompt, null, null, c.options(), null);
            case Item.Type t -> new ItemView(id, t.code(), prompt, t.sentence(), null, null, null);
            case Item.Fix f -> new ItemView(id, f.code(), prompt, null, f.wrong(), null, null);
            case Item.Build b -> new ItemView(id, b.code(), prompt, null, null, null,
                    SHUFFLER.shuffle(b.tiles(), b.answer(), seed ^ id.hashCode()));
        };
    }

    static List<ItemView> views(List<Item> items, LanguageChain chain, long seed) {
        return items.stream().map(item -> view(item, chain, seed)).toList();
    }

    /**
     * The JSON answer: a number (choose), a string (type, fix) or a list of
     * strings (build tiles in order).
     *
     * @throws DomainException (field {@code answer}) for anything else
     */
    static GivenAnswer given(Object answer) {
        if (answer instanceof Integer index) {
            return new GivenAnswer.Choice(index);
        }
        if (answer instanceof String text) {
            return new GivenAnswer.Text(text);
        }
        if (answer instanceof List<?> words && words.stream().allMatch(String.class::isInstance)) {
            return new GivenAnswer.Tiles(words.stream().map(String.class::cast).toList());
        }
        throw DomainException.of("answer", "Send an option number, a text, or the tiles in order.");
    }
}
