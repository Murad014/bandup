/**
 * Catalog context (learner side): browsing and filtering published
 * exercises (database requirement 7, design §5.0). Read-only: there are no
 * business rules to protect, so it has no domain layer — a query use case
 * and an SQL read model over Content's and Practice's tables (CQRS-lite).
 */
package com.bandup.api.catalog;
