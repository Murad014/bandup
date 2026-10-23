package com.bandup.api.identity.application;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.shared.application.UnauthenticatedException;

/** Use case: who am I (requirement 8.6). A token for a removed or disabled user is no longer valid. */
@Service
@RequiredArgsConstructor
public class GetMe {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public UserView get(UUID userId) {
        return users.findById(new UserId(userId))
                .filter(User::canAuthenticate)
                .map(UserView::of)
                .orElseThrow(() -> new UnauthenticatedException("Sign in to continue."));
    }
}
