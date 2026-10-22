package com.bandup.api.identity.domain.user.repository;

import java.util.Optional;

import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.identity.domain.user.valueobject.UserId;

/** Port: stored users. Implemented in infrastructure. */
public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    /** For the startup bootstrap: is there already someone with this role? */
    boolean existsWithRole(Role role);

    void save(User user);
}
