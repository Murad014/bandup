package com.bandup.api.catalog.application;

import java.util.List;

/**
 * What a learner asks the catalog for. Every filter is optional; filters
 * combine with AND, the values inside one filter with OR (database design §5.0).
 *
 * @param levels     CEFR levels, e.g. B1, B2
 * @param types      question type codes; matches an exercise with at least one group of one of them
 * @param topics     topic slugs
 * @param maxMinutes exercises without a timer always match
 * @param notDone    only exercises the signed-in learner hasn't attempted (ignored when anonymous)
 * @param requestedLanguage help language for topic names (may be null)
 */
public record CatalogQuery(String skill, String format, List<String> levels, List<String> types, List<String> topics,
        Integer maxMinutes, boolean notDone, int page, int size, String requestedLanguage) {

    public CatalogQuery {
        levels = levels == null ? List.of() : List.copyOf(levels);
        types = types == null ? List.of() : List.copyOf(types);
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}
