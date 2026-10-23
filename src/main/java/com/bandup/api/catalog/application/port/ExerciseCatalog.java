package com.bandup.api.catalog.application.port;

import java.util.Optional;
import java.util.UUID;

import com.bandup.api.catalog.application.CatalogFilters;
import com.bandup.api.catalog.application.CatalogPage;
import com.bandup.api.catalog.application.CatalogQuery;
import com.bandup.api.shared.domain.language.LanguageChain;

/** Port: the published exercises as catalog cards (SQL over the content and attempt tables). */
public interface ExerciseCatalog {

    /**
     * @param learner the signed-in learner's public id (for done / best score / notDone), or empty
     * @param help    language chain for topic names
     */
    CatalogPage find(CatalogQuery query, Optional<UUID> learner, LanguageChain help);

    CatalogFilters filters(LanguageChain help);
}
