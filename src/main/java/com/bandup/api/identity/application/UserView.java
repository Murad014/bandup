package com.bandup.api.identity.application;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.valueobject.Role;

/** What the API shows about a user (requirement 8.6). No email, no hash, no internal id. */
public record UserView(
        UUID id,
        String displayName,
        Set<String> roles,
        String uiLanguage,
        String supportLanguage,
        String targetLanguage) {

    public static UserView of(User user) {
        Set<String> roles = new TreeSet<>();
        user.roles().stream().map(Role::name).forEach(roles::add);
        return new UserView(user.id().value(), user.displayName().orElse(null), roles,
                user.uiLanguage().value(), user.supportLanguage().value(), user.targetLanguage().value());
    }
}
