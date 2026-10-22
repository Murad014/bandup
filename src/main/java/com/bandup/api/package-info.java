/**
 * Backend side: all business logic, organised as bounded contexts
 * ({@code shared}, {@code identity}, {@code practice}, {@code content}), each with
 * {@code domain}, {@code application}, {@code infrastructure} and {@code web}
 * layers. Exposed only as the REST API under {@code /api/v1/**}.
 *
 * <p>Dependencies point inwards: web → application → domain ← infrastructure.
 * Contexts use each other only by id and through ports; only {@code shared} may
 * be used by every context. All rules are enforced by ArchitectureTest.
 */
package com.bandup.api;
