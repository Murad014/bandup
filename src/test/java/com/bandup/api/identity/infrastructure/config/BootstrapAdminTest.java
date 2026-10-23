package com.bandup.api.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.identity.application.BootstrapAdmin;
import com.bandup.api.identity.application.Login;

/** The first admin is created from configuration at startup, once (requirement 8.9). */
@SpringBootTest(properties = {
        "bandup.auth.admin-email=Murad@Example.com",
        "bandup.auth.admin-password=a-strong-admin-password"})
@Import(TestcontainersConfiguration.class)
class BootstrapAdminTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired BootstrapAdmin bootstrapAdmin;
    @Autowired Login login;

    @Test
    void createsTheConfiguredAdminAtStartupOnlyOnce() {
        assertThat(jdbc.queryForList("""
                select u.email from app_user u join user_role r on r.user_id = u.id where r.role = 'ADMIN'
                """, String.class)).containsExactly("murad@example.com");

        assertThat(bootstrapAdmin.ensureAdmin("murad@example.com", "a-strong-admin-password"))
                .isEqualTo(BootstrapAdmin.Result.ADMIN_ALREADY_EXISTS);
        assertThat(jdbc.queryForObject("select count(*) from user_role where role = 'ADMIN'", Integer.class)).isEqualTo(1);
    }

    @Test
    void theAdminCanSignIn() {
        var signedIn = login.login(new Login.Command("murad@example.com", "a-strong-admin-password"));
        assertThat(signedIn.user().roles()).containsExactly("ADMIN");
    }

    @Test
    @Transactional
    void onlyTheLocalProfileAllowsAShortAdminPassword() {
        jdbc.update("delete from user_role where role = 'ADMIN'"); // as on a fresh database (rolled back)

        assertThatThrownBy(() -> bootstrapAdmin.ensureAdmin("admin@a.az", "admin")).isInstanceOf(DomainException.class);
        assertThat(bootstrapAdmin.ensureAdmin("admin@a.az", "admin", true)).isEqualTo(BootstrapAdmin.Result.CREATED);
        assertThat(bootstrapAdmin.ensureAdmin("admin@a.az", "admin", true)).isEqualTo(BootstrapAdmin.Result.ADMIN_ALREADY_EXISTS);
    }
}
