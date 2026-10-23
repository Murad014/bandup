/**
 * The <b>LoginThrottle</b> aggregate: failed sign-in attempts for one key (an
 * email address or a client address) and the lock they lead to.
 *
 * <ul>
 *   <li>{@code aggregate}: {@code LoginThrottle}</li>
 *   <li>{@code valueobject}: {@code ThrottleKey}, {@code ThrottlePolicy}</li>
 *   <li>{@code repository}: {@code LoginThrottleRepository}</li>
 * </ul>
 */
package com.bandup.api.identity.domain.throttle;
