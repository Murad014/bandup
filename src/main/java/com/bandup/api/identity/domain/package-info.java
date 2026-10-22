/**
 * Identity — domain layer. Pure Java (JDK only): no Spring, JPA, Jackson or Lombok.
 *
 * <p>Organised as one module (subpackage) per aggregate ({@code user},
 * {@code session}), each split by
 * building block: {@code aggregate} (the root), {@code entity}, {@code valueobject},
 * {@code repository} (port), {@code service} (domain services), and
 * {@code exception} if needed. Aggregates reference each other only by id.
 * Enforced by ArchitectureTest.
 */
package com.bandup.api.identity.domain;
