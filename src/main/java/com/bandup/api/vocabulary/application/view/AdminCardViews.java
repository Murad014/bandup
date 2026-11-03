package com.bandup.api.vocabulary.application.view;

import java.time.Instant;
import java.util.List;

import com.bandup.api.vocabulary.application.port.CardSummaries.CardSummary;

/**
 * The admin word card API's records (design §5.2). {@link Card} is both what
 * GET returns and what PUT takes back (with {@code version}); state fields are
 * ignored on input.
 */
public final class AdminCardViews {

    private AdminCardViews() {
    }

    public record CardPage(List<CardSummary> cards, int page, int size, long total) {
    }

    public record Card(Long id, Long version, String status, String source, Integer revision, Integer saves,
            String lastError, Instant updatedAt, String lemma, List<String> forms, String pos, String level, String ipa,
            Meaning meaning, List<String> examples, List<String> collocations, List<String> family, Gap gap, Pair pair,
            List<String> wrongMeanings) {
    }

    public record Meaning(String en, String az) {
    }

    public record Gap(String sentence, String answer) {
    }

    public record Pair(String sentence, List<String> options, Integer answer) {
    }
}
