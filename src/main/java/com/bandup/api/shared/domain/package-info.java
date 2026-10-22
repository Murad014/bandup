/**
 * Shared kernel — domain layer. Pure Java (JDK only): no Spring, JPA, Jackson or Lombok.
 *
 * <p>Organised as modules used by every context: {@code language} (codes,
 * translations, fallback rules), {@code error} (rule violations) and
 * {@code ddd} (the building-block markers).
 */
package com.bandup.api.shared.domain;
