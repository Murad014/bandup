/**
 * Frontend side: Thymeleaf page controllers that serve HTML shells (templates,
 * JS, CSS). No business logic and no data: the page's JavaScript calls the REST
 * API ({@code /api/v1/**}) with a JWT.
 *
 * <p>Must never depend on {@code com.bandup.api} (enforced by ArchitectureTest);
 * the two sides talk only over HTTP.
 */
package com.bandup.ui;
