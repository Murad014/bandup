package com.bandup.api.identity.domain.user.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;
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
        assertThat(aysel.canAuthenticate()).isTrue();
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
        User murad = User.createAdmin(Email.of("murad@example.com"), HASH, "Murad");
        assertThat(murad.roles()).containsExactly(Role.ADMIN);
        assertThat(murad.canAuthor()).isTrue();
    }

    @Test
    void onlyActiveUsersCanAuthenticate() {
        User disabled = learner();
        disabled.disable();
        User deleted = learner();
        deleted.markDeleted();

        assertThat(learner().canAuthenticate()).isTrue();
        assertThat(disabled.canAuthenticate()).isFalse();
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

    private static User learner() {
        return User.registerLearner(Email.of("aysel@example.com"), HASH, "Aysel", AZ, AZ);
    }
}
