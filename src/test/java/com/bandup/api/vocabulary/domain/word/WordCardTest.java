package com.bandup.api.vocabulary.domain.word;

import static com.bandup.api.vocabulary.domain.word.Cards.FORMS;
import static com.bandup.api.vocabulary.domain.word.Cards.LEMMA;
import static com.bandup.api.vocabulary.domain.word.Cards.POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.valueobject.CardSource;
import com.bandup.api.vocabulary.domain.word.valueobject.CardStatus;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

class WordCardTest {

    private final WordCard card = WordCard.preparing(new WordId(1), new WordForm("riverbanks"));

    @Test
    void aNewCardOwnsTheAskedFormWhileTheAiWritesIt() {
        assertThat(card.status()).isEqualTo(CardStatus.PREPARING);
        assertThat(card.forms()).containsExactly(new WordForm("riverbanks"));
        assertThat(card.lemma()).isEmpty();
        assertThat(card.display()).isEqualTo("riverbanks");
    }

    @Test
    void validContentMakesItReadyAndKeepsTheAskedForm() {
        card.ready(LEMMA, List.of(new WordForm("riverbank")), Cards.riverbank(), POLICY);
        assertThat(card.status()).isEqualTo(CardStatus.READY);
        assertThat(card.forms()).containsExactlyInAnyOrderElementsOf(FORMS);
        assertThat(card.display()).isEqualTo("riverbank");
        assertThat(card.revision()).isEqualTo(1);
        assertThat(card.source()).isEqualTo(CardSource.AI);
    }

    @Test
    void invalidContentChangesNothing() {
        assertThatThrownBy(() -> card.ready(LEMMA, FORMS, Cards.with(c -> c.level("Z9")), POLICY))
                .isInstanceOf(DomainException.class).hasMessageContaining("level");
        assertThat(card.status()).isEqualTo(CardStatus.PREPARING);
        assertThat(card.content()).isEmpty();
    }

    @Test
    void twoBadAnswersMakeItFailedThenTryAgainStartsOver() {
        card.failed("bad JSON");
        assertThat(card.status()).isEqualTo(CardStatus.PREPARING);
        assertThat(card.canTryAgain()).isTrue();
        card.failed("bad JSON again");
        assertThat(card.status()).isEqualTo(CardStatus.FAILED);
        assertThat(card.lastError()).contains("bad JSON again");
        card.retry();
        assertThat(card.status()).isEqualTo(CardStatus.PREPARING);
        assertThat(card.attempts()).isZero();
        card.ready(LEMMA, FORMS, Cards.riverbank(), POLICY);
        assertThat(card.isReady()).isTrue();
    }

    @Test
    void aRejectedTextKeepsItsForm() {
        card.rejected("a person's name");
        assertThat(card.status()).isEqualTo(CardStatus.REJECTED);
        assertThat(card.forms()).containsExactly(new WordForm("riverbanks"));
        assertThatThrownBy(card::retry).isInstanceOf(DomainException.class);
    }

    @Test
    void regenerateKeepsTheContentMeanwhile() {
        card.ready(LEMMA, FORMS, Cards.riverbank(), POLICY);
        card.retry();
        assertThat(card.status()).isEqualTo(CardStatus.PREPARING);
        assertThat(card.content()).isPresent();
    }

    @Test
    void anAdminEditMakesItReadyWithANewRevision() {
        assertThatThrownBy(() -> card.edit(LEMMA, FORMS, Cards.riverbank(), POLICY)).isInstanceOf(DomainException.class);
        card.rejected("unknown");
        card.edit(LEMMA, FORMS, Cards.riverbank(), POLICY);
        assertThat(card.status()).isEqualTo(CardStatus.READY);
        assertThat(card.source()).isEqualTo(CardSource.ADMIN);
        card.edit(LEMMA, FORMS, Cards.with(c -> c.level("B2")), POLICY);
        assertThat(card.revision()).isEqualTo(2);
        assertThat(card.content().orElseThrow().level()).isEqualTo("B2");
    }

    @Test
    void transitionsFromTheWrongStatusAreRefused() {
        card.ready(LEMMA, FORMS, Cards.riverbank(), POLICY);
        assertThatThrownBy(() -> card.failed("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> card.rejected("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> card.ready(LEMMA, FORMS, Cards.riverbank(), POLICY)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void formsCanBeAdded() {
        card.ready(LEMMA, FORMS, Cards.riverbank(), POLICY);
        card.addForms(List.of(new WordForm("river bank")));
        assertThat(card.forms()).contains(new WordForm("river bank"));
    }
}
