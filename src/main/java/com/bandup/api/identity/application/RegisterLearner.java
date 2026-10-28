package com.bandup.api.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;

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
 * Use case: someone signs up as a Learner or an Instructor (requirements 8.1,
 * 12.1–12.2). Reports every invalid field at once. The account can't sign in
 * until the emailed confirmation link is opened; an Instructor sign-up waits
 * for an admin.
 *
 * <p>Deliberately not one transaction: BCrypt takes ~250 ms and must not hold
 * a database connection. The single insert runs in its own transaction; a
 * duplicate email that slips past the early check is still stopped by the
 * unique constraint (409).
 */
@Service
@RequiredArgsConstructor
public class RegisterLearner {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final LanguageCatalog languages;
    private final AccountEmails emails;
    private final Clock clock;

    /**
     * @param uiLanguage      language of menus; English if blank
     * @param supportLanguage language of help text; English if blank
     * @param accountType     {@code LEARNER} (also when blank) or {@code INSTRUCTOR}
     */
    public record Command(String email, String password, String displayName, String uiLanguage, String supportLanguage,
            String accountType) {

        public Command(String email, String password, String displayName, String uiLanguage, String supportLanguage) {
            this(email, password, displayName, uiLanguage, supportLanguage, null);
        }


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
        Boolean instructor = collect(violations, () -> wantsInstructor(command.accountType()));
        if (!violations.isEmpty()) {
            throw new DomainException(violations);
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        Instant now = clock.instant();
        User user = User.register(email, hasher.hash(password), displayName, ui, support, instructor, now);
        users.save(user);
        emails.sendConfirmation(user, now);
        return UserView.of(user);
    }

    private static boolean wantsInstructor(String accountType) {
        if (accountType == null || accountType.isBlank() || accountType.equals("LEARNER")) {
            return false;
        }
        if (accountType.equals("INSTRUCTOR")) {
            return true;
        }
        throw DomainException.of("accountType", "Choose Learner or Instructor.");
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
