/**
 * The <b>Session</b> aggregate: one login session, i.e. the family of
 * rotating refresh tokens created by one sign-in. Split by building block:
 *
 * <ul>
 *   <li>{@code aggregate}: {@code Session} (root: rotation, reuse detection, revoke)</li>
 *   <li>{@code entity}: {@code RefreshToken} (immutable; only the session swaps tokens in its list)</li>
 *   <li>{@code valueobject}: {@code SessionId}, {@code TokenHash}</li>
 *   <li>{@code repository}: {@code SessionRepository} (port, loads and saves whole sessions)</li>
 * </ul>
 *
 * <p>References the User aggregate only by {@code UserId}.
 */
package com.bandup.api.identity.domain.session;
