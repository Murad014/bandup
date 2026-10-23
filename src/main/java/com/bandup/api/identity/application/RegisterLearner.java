package com.bandup.api.identity.application;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.bandup.api.identity.application.port.PasswordHasher;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.NewPassword;
import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.LanguageFallbacks;

/**
 * Use case: a learner signs up (requirement 8.1). Reports every invalid field
 * at once.
 *
 * <p>Deliberately not one transaction: BCrypt takes ~250 ms and must not hold
 * a database connection. The single insert runs in its own transaction; a
 * duplicate email that slips past the early check is still stopped by the
 * unique constraint (409).
 */
@Service
public class RegisterLearner {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final LanguageCatalog languages;

    public RegisterLearner(UserRepository users, PasswordHasher hasher, LanguageCatalog languages) {
        this.users = users;
        this.hasher = hasher;
        this.languages = languages;
    }

    /**
     * @param uiLanguage      language of menus; English if blank
     * @param supportLanguage language of help text; English if blank
     */
    public record Command(String email, String password, String displayName, String uiLanguage, String supportLanguage) {

        @Override
        public String toString() {
            return "RegisterLearner.Command[email=" + email + "]";
        }
    }

    public UserView register(Command command) {
        List<Violation> violations = new ArrayList<>();
        LanguageFallbacks known = languages.fallbacks();
        Email email = collect(violations, () -> Email.of(command.email()));
        NewPassword password = collect(violations, () -> NewPassword.of(command.password()));
        LanguageCode ui = collect(violations, () -> knownLanguage(known, "uiLanguage", command.uiLanguage()));
        LanguageCode support = collect(violations, () -> knownLanguage(known, "supportLanguage", command.supportLanguage()));
        String displayName = collect(violations, () -> User.displayName(command.displayName()));
        if (!violations.isEmpty()) {
            throw new DomainException(violations);
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = User.registerLearner(email, hasher.hash(password), displayName, ui, support);
        users.save(user);
        return UserView.of(user);
    }

    private static LanguageCode knownLanguage(LanguageFallbacks known, String field, String raw) {
        if (raw == null || raw.isBlank()) {
            return LanguageCode.ENGLISH;
        }
        LanguageCode code;
        try {
            code = LanguageCode.of(raw);
        } catch (DomainException malformed) {
            throw DomainException.of(field, "Unknown language: '" + raw + "'.");
        }
        return known.storedSpelling(code)
                .orElseThrow(() -> DomainException.of(field, "Unknown language: '" + raw + "'."));
    }

    private static <T> T collect(List<Violation> violations, Supplier<T> parse) {
        try {
            return parse.get();
        } catch (DomainException e) {
            violations.addAll(e.violations());
            return null;
        }
    }
}
