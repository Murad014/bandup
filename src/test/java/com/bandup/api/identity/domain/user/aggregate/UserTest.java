package com.bandup.api.identity.domain.user.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;
import com.bandup.api.identity.domain.user.valueobject.InstructorStatus;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.identity.domain.user.valueobject.UserStatus;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.language.LanguageCode;

class UserTest {

    private static final PasswordHash HASH = new PasswordHash("$2a$10$abcdefghijklmnopqrstuv");
    private static final LanguageCode AZ = LanguageCode.of("az");

    @Test
    void aRegisteredLearnerIsActiveLearnsEnglishAndHasOnlyTheLearnerRole() {
        User aysel = User.registerLearner(Email.of("aysel@example.com"), HASH, "Aysel", AZ, AZ);

        assertThat(aysel.roles()).containsExactly(Role.LEARNER);
        assertThat(aysel.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(aysel.isEmailVerified()).isFalse();
        assertThat(aysel.canAuthenticate()).isFalse();         // until the emailed link is opened
        assertThat(aysel.awaitsEmailConfirmation()).isTrue();
        assertThat(aysel.instructorStatus()).isEqualTo(InstructorStatus.NONE);
        aysel.verifyEmail(NOW);
        assertThat(aysel.canAuthenticate()).isTrue();
        assertThat(aysel.awaitsEmailConfirmation()).isFalse();
        assertThat(aysel.canAuthor()).isFalse();
        assertThat(aysel.targetLanguage()).isEqualTo(LanguageCode.ENGLISH);
        assertThat(aysel.supportLanguage()).isEqualTo(AZ);
        assertThat(aysel.displayName()).contains("Aysel");
    }

    @Test
    void everyNewUserGetsItsOwnId() {
        User a = User.registerLearner(Email.of("a@example.com"), HASH, null, AZ, AZ);
        User b = User.registerLearner(Email.of("b@example.com"), HASH, null, AZ, AZ);
        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void theBootstrapAdminCanUseTheAdminApi() {
        User murad = User.createAdmin(Email.of("murad@example.com"), HASH, "Murad", java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(murad.roles()).containsExactly(Role.ADMIN);
        assertThat(murad.canAuthor()).isTrue();
    }

    @Test
    void onlyActiveUsersWithAConfirmedEmailCanAuthenticate() {
        User disabled = learner();
        disabled.disable();
        User deleted = learner();
        deleted.markDeleted();

        assertThat(learner().canAuthenticate()).isTrue();
        assertThat(disabled.canAuthenticate()).isFalse();
        assertThat(disabled.awaitsEmailConfirmation()).isFalse(); // a disabled account just gets "wrong email or password"
        assertThat(deleted.canAuthenticate()).isFalse();
        assertThat(UserStatus.ACTIVE.canAuthenticate()).isTrue();
        assertThat(UserStatus.DISABLED.canAuthenticate()).isFalse();
        assertThat(UserStatus.DELETED.canAuthenticate()).isFalse();
    }

    @Test
    void authorRoleAlsoGrantsTheAdminApi() {
        User author = User.restore(UserId.newId(), Email.of("leyla@example.com"), HASH, "Leyla", AZ, AZ,
                LanguageCode.ENGLISH, EnumSet.of(Role.AUTHOR, Role.TRANSLATOR), UserStatus.ACTIVE);
        assertThat(author.canAuthor()).isTrue();
        assertThat(author.hasRole(Role.TRANSLATOR)).isTrue();
        assertThat(author.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void displayNameIsTrimmedOptionalAndLimited() {
        assertThat(User.registerLearner(Email.of("a@example.com"), HASH, "  Aysel  ", AZ, AZ).displayName()).contains("Aysel");
        assertThat(User.registerLearner(Email.of("a@example.com"), HASH, "   ", AZ, AZ).displayName()).isEmpty();
        assertThatThrownBy(() -> User.registerLearner(Email.of("a@example.com"), HASH, "x".repeat(81), AZ, AZ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("displayName");
    }

    @Test
    void needsAtLeastOneRole() {
        assertThatThrownBy(() -> User.restore(UserId.newId(), Email.of("a@example.com"), HASH, null, AZ, AZ,
                LanguageCode.ENGLISH, Set.of(), UserStatus.ACTIVE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rolesCannotBeChangedFromOutside() {
        User aysel = learner();
        assertThatThrownBy(() -> aysel.roles().add(Role.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(aysel.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void passwordCanBeChangedButHashesAreNeverPrinted() {
        User aysel = learner();
        PasswordHash newHash = new PasswordHash("$2a$10$zyxwvutsrqponmlkjihgfe");
        aysel.changePassword(newHash);
        assertThat(aysel.passwordHash()).isEqualTo(newHash);
        assertThat(newHash.toString()).doesNotContain("zyxw");
        assertThatThrownBy(() -> new PasswordHash(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isEqualByIdentity() {
        UserId id = UserId.newId();
        User a = User.restore(id, Email.of("a@example.com"), HASH, "A", AZ, AZ, LanguageCode.ENGLISH,
                EnumSet.of(Role.LEARNER), UserStatus.ACTIVE);
        User sameIdOtherData = User.restore(id, Email.of("b@example.com"), HASH, "B", AZ, AZ, LanguageCode.ENGLISH,
                EnumSet.of(Role.ADMIN), UserStatus.DISABLED);

        assertThat(a).isEqualTo(sameIdOtherData).hasSameHashCodeAs(sameIdOtherData);
        assertThat(a).isNotEqualTo(learner());
    }

    /** A learner who opened the confirmation link. */
    private static User learner() {
        User aysel = User.registerLearner(Email.of("aysel@example.com"), HASH, "Aysel", AZ, AZ);
        aysel.verifyEmail(java.time.Instant.parse("2026-10-24T09:00:00Z"));
        return aysel;
    }

    // ------------------------------------------------------------ email and Instructor requests (requirement 12)

    private static final java.time.Instant NOW = java.time.Instant.parse("2026-10-24T10:00:00Z");

    @Test
    void confirmingTheEmailTwiceKeepsTheFirstTime() {
        User aysel = User.registerLearner(Email.of("aysel@example.com"), HASH, null, AZ, AZ);
        aysel.verifyEmail(NOW);
        aysel.verifyEmail(NOW.plusSeconds(60));
        assertThat(aysel.emailVerifiedAt()).contains(NOW);
    }

    @Test
    void theFirstAdminIsConfirmedFromTheStart() {
        User admin = User.createAdmin(Email.of("owner@example.com"), HASH, null, NOW);
        assertThat(admin.canAuthenticate()).isTrue();
    }

    @Test
    void anInstructorSignUpIsAPendingRequestThatOnlyApprovalTurnsIntoAnAuthor() {
        User leyla = User.register(Email.of("leyla@example.com"), HASH, "Leyla", AZ, AZ, true, NOW);
        assertThat(leyla.instructorStatus()).isEqualTo(InstructorStatus.PENDING);
        assertThat(leyla.instructorRequestedAt()).contains(NOW);
        assertThat(leyla.roles()).containsExactly(Role.LEARNER);
        assertThat(leyla.canAuthor()).isFalse();

        leyla.approveInstructor();
        assertThat(leyla.instructorStatus()).isEqualTo(InstructorStatus.APPROVED);
        assertThat(leyla.roles()).containsExactlyInAnyOrder(Role.LEARNER, Role.AUTHOR);
        assertThat(leyla.canAuthor()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(leyla::approveInstructor).isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(leyla::rejectInstructor).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRejectedRequestStaysALearnerAndALearnerHasNothingToApprove() {
        User rasim = User.register(Email.of("rasim@example.com"), HASH, null, AZ, AZ, true, NOW);
        rasim.rejectInstructor();
        assertThat(rasim.instructorStatus()).isEqualTo(InstructorStatus.REJECTED);
        assertThat(rasim.canAuthor()).isFalse();

        User aysel = User.registerLearner(Email.of("aysel@example.com"), HASH, null, AZ, AZ);
        org.assertj.core.api.Assertions.assertThatThrownBy(aysel::approveInstructor).isInstanceOf(IllegalStateException.class);
    }
}
