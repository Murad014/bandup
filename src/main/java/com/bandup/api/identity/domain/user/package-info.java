/**
 * The <b>User</b> aggregate, split by building block:
 *
 * <ul>
 *   <li>{@code aggregate}: {@code User} (root)</li>
 *   <li>{@code valueobject}: {@code UserId} (how other aggregates refer to a user), {@code Email},
 *       {@code NewPassword}, {@code PasswordHash}, {@code Role}, {@code UserStatus}</li>
 *   <li>{@code repository}: {@code UserRepository} (port)</li>
 * </ul>
 */
package com.bandup.api.identity.domain.user;
