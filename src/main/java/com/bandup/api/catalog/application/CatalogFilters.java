package com.bandup.api.catalog.application;

import java.util.List;

/** The filter choices worth offering: only values some published exercise has. */
public record CatalogFilters(List<String> skills, List<String> formats, List<String> levels, List<String> types,
        List<ExerciseCard.Topic> topics) {

    public CatalogFilters {
        skills = List.copyOf(skills);
        formats = List.copyOf(formats);
        levels = List.copyOf(levels);
        types = List.copyOf(types);
        topics = List.copyOf(topics);
    }
}
