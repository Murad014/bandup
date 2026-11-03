package com.bandup.api.vocabulary.infrastructure.config;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bandup.api.vocabulary.application.CardWriter;
import com.bandup.api.vocabulary.application.port.CardQueue;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * {@link CardQueue}: after the transaction commits, the card is written on a
 * small private pool (two writers at a time; a rolled-back save never calls
 * the AI). The pool is not a bean, so Spring Boot's own task executor stays.
 */
@Component
@Slf4j
class AfterCommitCardQueue implements CardQueue, DisposableBean {

    private final CardWriter writer;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    AfterCommitCardQueue(CardWriter writer) {
        this.writer = writer;
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("card-writer-");
        executor.initialize();
    }

    @Override
    public void writeAfterCommit(WordId card) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(card);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(card);
            }
        });
    }

    private void submit(WordId card) {
        executor.execute(() -> {
            try {
                writer.write(card);
            } catch (RuntimeException e) {
                log.error("Writing word card {} failed", card.value(), e);
            }
        });
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
