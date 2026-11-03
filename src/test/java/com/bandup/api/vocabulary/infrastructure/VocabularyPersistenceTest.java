package com.bandup.api.vocabulary.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.repository.SavedWordRepository;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSource;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordState;
import com.bandup.api.vocabulary.domain.session.aggregate.WordSession;
import com.bandup.api.vocabulary.domain.session.repository.WordSessionRepository;
import com.bandup.api.vocabulary.domain.session.service.AnswerNormaliser;
import com.bandup.api.vocabulary.domain.session.service.TaskBuilder;
import com.bandup.api.vocabulary.domain.session.valueobject.GivenAnswer;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionId;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionKind;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionWord;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskId;
import com.bandup.api.vocabulary.domain.word.Cards;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.CardStatus;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

/** The vocabulary tables and adapters (design §7) against PostgreSQL. Rolled back. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class VocabularyPersistenceTest {

    @Autowired WordCardRepository cards;
    @Autowired SavedWordRepository saved;
    @Autowired WordSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;

    private LearnerId leyla;

    @BeforeEach
    void aLearner() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user (public_id, email, ui_language, support_language) values (?, ?, 'en', 'en')",
                id, id + "@example.com");
        leyla = new LearnerId(id);
    }

    @Test
    void aCardRoundTripsWithItsForms() {
        WordCard card = WordCard.preparing(cards.nextId(), new WordForm("riverbanks"));
        assertThat(cards.addIfFormFree(card)).isTrue();
        WordCard loaded = cards.findByForm(new WordForm("riverbanks")).orElseThrow();
        assertThat(loaded.status()).isEqualTo(CardStatus.PREPARING);
        assertThat(loaded.version()).isZero();

        WordCard locked = cards.lock(card.id()).orElseThrow();
        locked.ready(Cards.LEMMA, Cards.FORMS, Cards.riverbank(), Cards.POLICY);
        assertThat(cards.save(locked)).isEqualTo(1);

        WordCard ready = cards.findByForm(new WordForm("riverbank")).orElseThrow();
        assertThat(ready.id()).isEqualTo(card.id());
        assertThat(ready.forms()).containsExactly(new WordForm("riverbank"), new WordForm("riverbanks"));
        assertThat(ready.content()).contains(Cards.riverbank());
        assertThat(ready.lemma()).contains(Cards.LEMMA);
        assertThat(cards.findByForms(List.of(new WordForm("riverbanks"), new WordForm("nothing")))).containsExactly(ready);
    }

    @Test
    void oneCardPerSpelling() {
        WordCard first = WordCard.preparing(cards.nextId(), new WordForm("thrive"));
        WordCard second = WordCard.preparing(cards.nextId(), new WordForm("thrive"));
        assertThat(cards.addIfFormFree(first)).isTrue();
        assertThat(cards.addIfFormFree(second)).as("the form is taken").isFalse();
        assertThat(cards.findById(second.id())).isEmpty();

        WordCard other = WordCard.preparing(cards.nextId(), new WordForm("thrives"));
        cards.addIfFormFree(other);
        WordCard loaded = cards.lock(other.id()).orElseThrow();
        loaded.addForms(List.of(new WordForm("thrive")));
        assertThatThrownBy(() -> cards.save(loaded)).isInstanceOf(ConflictException.class).hasMessageContaining("thrive");
    }

    @Test
    void aStaleVersionIsAConflict() {
        WordCard card = WordCard.preparing(cards.nextId(), new WordForm("abundant"));
        cards.addIfFormFree(card);
        WordCard a = cards.lock(card.id()).orElseThrow();
        WordCard b = cards.lock(card.id()).orElseThrow();
        a.failed("x");
        cards.save(a);
        b.failed("y");
        assertThatThrownBy(() -> cards.save(b)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aSavedWordRoundTripsAndKeepsItsPassage() {
        WordCard card = readyCard();
        SavedWord word = SavedWord.save(leyla, card.id(), new WordSource("yellowstone-wolves", "Willows grew back."),
                LocalDate.of(2026, 11, 3));
        saved.save(word);
        SavedWord loaded = saved.findById(word.id()).orElseThrow();
        assertThat(loaded.source().exerciseSlug()).contains("yellowstone-wolves");
        assertThat(loaded.source().sentenceText()).contains("Willows grew back.");
        assertThat(loaded.state()).isEqualTo(WordState.NEW);
        loaded.learned(LocalDate.of(2026, 11, 3));
        saved.save(loaded);
        SavedWord learning = saved.find(leyla, card.id()).orElseThrow();
        assertThat(learning.box()).hasValueSatisfying(b -> assertThat(b.value()).isEqualTo(1));
        assertThat(learning.dueOn()).contains(LocalDate.of(2026, 11, 4));
        assertThat(saved.findByWord(card.id())).hasSize(1);

        SavedWord twice = SavedWord.save(leyla, card.id(), WordSource.typed(), LocalDate.of(2026, 11, 3));
        assertThatThrownBy(() -> saved.save(twice)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSavedCardCantBeDeleted() {
        WordCard card = readyCard();
        saved.save(SavedWord.save(leyla, card.id(), WordSource.typed(), LocalDate.of(2026, 11, 3)));
        assertThatThrownBy(() -> cards.delete(card)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSessionRoundTripsWithEveryTaskType() {
        WordCard card = readyCard();
        SavedWord word = SavedWord.save(leyla, card.id(), WordSource.typed(), LocalDate.of(2026, 11, 3));
        saved.save(word);
        List<SessionWord> words = List.of(snapshot(word, "riverbank"), snapshot(word, "erosion"), snapshot(word, "thrive"),
                snapshot(word, "decline"), snapshot(word, "abundant"));
        Instant now = Instant.parse("2026-11-03T10:00:00Z");
        WordSession session = WordSession.start(SessionId.random(), leyla, SessionKind.LEARN, words,
                new TaskBuilder().build(SessionKind.LEARN, words, 9), now);
        session.answer(TaskId.of(3), new GivenAnswer.Text("wrong"), new AnswerNormaliser()::normalise, now);
        sessions.save(session);

        WordSession loaded = sessions.findById(session.id()).orElseThrow();
        assertThat(loaded.tasks()).isEqualTo(session.tasks());
        assertThat(loaded.words()).isEqualTo(session.words());
        assertThat(loaded.queue()).isEqualTo(session.queue());
        assertThat(loaded.retries()).isEqualTo(1);
        assertThat(loaded.tasks()).extracting(t -> t.type()).contains("meaning", "match", "gap", "type", "listen", "pair", "translate");

        assertThat(sessions.deleteStartedBefore(now)).isZero();
        assertThat(sessions.deleteStartedBefore(now.plusSeconds(1))).isEqualTo(1);
    }

    private WordCard readyCard() {
        WordCard card = WordCard.preparing(cards.nextId(), new WordForm("riverbank"));
        cards.addIfFormFree(card);
        WordCard locked = cards.lock(card.id()).orElseThrow();
        locked.ready(Cards.LEMMA, Cards.FORMS, Cards.riverbank(), Cards.POLICY);
        cards.save(locked);
        return cards.findById(card.id()).orElseThrow();
    }

    private static SessionWord snapshot(SavedWord saved, String word) {
        return new SessionWord(saved.id(), word, List.of(word), "noun", "B1", "/x/", "meaning of " + word, "az " + word,
                List.of("One " + word + ".", "Two " + word + "."), List.of("a " + word), List.of(), "From the passage.",
                "The wolves", "A ___ here.", word, "The ___ there.", List.of(word, "b", "c", "d"), 0,
                List.of("w1 " + word, "w2 " + word, "w3 " + word), 0);
    }
}
