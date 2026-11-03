package com.bandup.api.vocabulary.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.CardSource;
import com.bandup.api.vocabulary.domain.word.valueobject.CardStatus;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * {@link WordCardRepository}: the card on JPA, its forms with plain SQL. The
 * form's primary key makes "one card per spelling" a database rule, so two
 * learners saving the same new word at once can't create two cards.
 */
@Repository
@RequiredArgsConstructor
class JpaWordCardRepository implements WordCardRepository {

    private final EntityManager em;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<WordCard> findByForm(WordForm form) {
        return jdbc.sql("select word_id from vocab_word_form where form = ?").param(form.value()).query(Long.class)
                .optional().flatMap(id -> findById(new WordId(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WordCard> findByForms(Collection<WordForm> forms) {
        if (forms.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select distinct word_id from vocab_word_form where form in (:forms)")
                .param("forms", forms.stream().map(WordForm::value).toList()).query(Long.class).list().stream()
                .map(id -> findById(new WordId(id)).orElseThrow()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WordCard> findById(WordId id) {
        return Optional.ofNullable(em.find(WordCardEntity.class, id.value())).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<WordCard> lock(WordId id) {
        WordCardEntity row = em.find(WordCardEntity.class, id.value(), LockModeType.PESSIMISTIC_WRITE);
        if (row == null) {
            return Optional.empty();
        }
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(toDomain(row));
    }

    @Override
    @Transactional
    public WordId nextId() {
        return new WordId(((Number) em.createNativeQuery("select nextval(pg_get_serial_sequence('vocab_word', 'id'))")
                .getSingleResult()).longValue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WordId> findPreparingUpdatedBefore(Instant cutoff) {
        return jdbc.sql("select id from vocab_word where status = 'PREPARING' and updated_at < ? order by updated_at")
                .param(java.sql.Timestamp.from(cutoff)).query(Long.class).list().stream().map(WordId::new).toList();
    }

    @Override
    @Transactional
    public boolean addIfFormFree(WordCard card) {
        WordForm form = card.forms().iterator().next();
        WordCardEntity row = new WordCardEntity();
        row.id = card.id().value();
        row.createdAt = clock.instant();
        copy(card, row);
        row.version = 0;
        em.persist(row);
        em.flush();
        int added = jdbc.sql("insert into vocab_word_form (form, word_id) values (?, ?) on conflict (form) do nothing")
                .params(form.value(), card.id().value()).update();
        if (added == 0) {
            em.remove(row);
            em.flush();
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public long save(WordCard card) {
        WordCardEntity row = em.find(WordCardEntity.class, card.id().value(), LockModeType.PESSIMISTIC_WRITE);
        if (row == null) {
            throw new IllegalStateException("No card " + card.id());
        }
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        if (row.version != card.version()) {
            throw new ConflictException("This word was changed by someone else. Reload it and try again.");
        }
        copy(card, row);
        row.version++;
        em.flush();
        syncForms(card);
        return row.version;
    }

    private void syncForms(WordCard card) {
        List<String> wanted = card.forms().stream().map(WordForm::value).toList();
        jdbc.sql("delete from vocab_word_form where word_id = :id and form not in (:forms)")
                .param("id", card.id().value()).param("forms", wanted).update();
        for (String form : wanted) {
            int added = jdbc.sql("insert into vocab_word_form (form, word_id) values (?, ?) on conflict (form) do nothing")
                    .params(form, card.id().value()).update();
            if (added == 0 && !jdbc.sql("select word_id from vocab_word_form where form = ?").param(form)
                    .query(Long.class).single().equals(card.id().value())) {
                throw new ConflictException("“" + form + "” already belongs to another word.");
            }
        }
    }

    @Override
    @Transactional
    public void delete(WordCard card) {
        WordCardEntity row = em.find(WordCardEntity.class, card.id().value());
        if (row != null) {
            em.remove(row);
            em.flush();
        }
    }

    private void copy(WordCard card, WordCardEntity row) {
        row.lemma = card.lemma().map(Lemma::value).orElse(null);
        row.status = card.status().name();
        row.source = card.source().name();
        row.revision = card.revision();
        row.attempts = card.attempts();
        row.lastError = card.lastError().orElse(null);
        row.content = card.content().map(CardJson::write).orElse(null);
        row.updatedAt = clock.instant();
    }

    private WordCard toDomain(WordCardEntity row) {
        Set<WordForm> forms = new LinkedHashSet<>(jdbc.sql("""
                select form from vocab_word_form where word_id = ? order by (form = ?) desc, form""")
                .params(row.id, row.lemma == null ? "" : row.lemma).query(String.class).list().stream()
                .map(WordForm::new).toList());
        return WordCard.restore(new WordId(row.id), row.lemma == null ? null : new Lemma(row.lemma), forms,
                CardStatus.valueOf(row.status), row.content == null ? null : CardJson.read(row.content),
                CardSource.valueOf(row.source), row.revision, row.attempts, row.lastError, row.version);
    }
}
