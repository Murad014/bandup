package com.bandup.api.catalog.application;

import java.util.List;

/** One page of the catalog. */
public record CatalogPage(List<ExerciseCard> items, int page, int size, long totalItems) {

    public CatalogPage {
        items = List.copyOf(items);
    }
}
