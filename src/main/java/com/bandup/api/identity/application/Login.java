package com.bandup.api.identity.application;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bandup.api.identity.application.port.PasswordHasher;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.NewPassword;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;
import com.bandup.api.shared.application.UnauthenticatedException;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * Use case: sign in with email and password (requirements 8.2, 8.3, 8.10).
 * A wrong email, a wrong password and a disabled account all get the same
 * answer, and take about the same time (a dummy hash is checked when the email
 * is unknown), so the response doesn't reveal which emails exist.
 *
 * <p>Deliberately not one transaction: BCrypt takes ~250 ms and must not hold
 * a database connection. The user lookup and the token insert each run in
 * their own short transaction in the repositories.
 */
@Service
public class Login {

    static final String WRONG_CREDENTIALS = "Wrong email or password.";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final SessionIssuer sessions;
    private final Clock clock;
    private final PasswordHash timingDummy;

    public Login(UserRepository users, PasswordHasher hasher, SessionIssuer sessions, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.clock = clock;
        this.timingDummy = hasher.hash(NewPassword.of("timing-dummy-password"));
    }

    public record Command(String email, String password) {

        @Override
        public String toString() {
            return "Login.Command[email=" + email + "]";
        }
    }

    public SignedIn login(Command command) {
        Optional<User> found = parseEmail(command.email()).flatMap(users::findByEmail);
        boolean passwordMatches = hasher.matches(command.password(), found.map(User::passwordHash).orElse(timingDummy));
        if (found.isEmpty() || !passwordMatches || !found.get().canAuthenticate()) {
            throw new UnauthenticatedException(WRONG_CREDENTIALS);
        }
        return sessions.start(found.get(), clock.instant());
    }

    private static Optional<Email> parseEmail(String raw) {
        try {
            return Optional.of(Email.of(raw));
        } catch (DomainException malformed) {
            return Optional.empty();
        }
    }
}
