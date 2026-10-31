package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bandup.RecordingMailer;

/** Requirement 12 in the browser: Instructor sign-up and approval, forgot / reset password. */
class AccountEmailsBrowserTest extends BrowserTestBase {

    @Autowired RecordingMailer mailbox;

    @Test
    void anInstructorSignsUpAndAnAdminApprovesThemOnTheInstructorsPage() throws Exception {
        String email = "teacher-" + UUID.randomUUID() + "@bandup.test";
        HeadlessChrome.Tab tab = chrome.newTab(url("/register"));
        tab.eval("document.querySelector('input[name=accountType][value=INSTRUCTOR]').click()");
        tab.eval(type("#displayName", "Teacher Leyla"));
        tab.eval(type("#email", email));
        tab.eval(type("#password", PASSWORD));
        tab.eval("document.querySelector('[data-register-form] button[type=submit]').click()");
        tab.waitFor("!document.querySelector('[data-check-email]').hidden");
        assertThat(tab.eval("document.querySelector('[data-instructor-note]').hidden").asBoolean()).isFalse();

        HeadlessChrome.Tab admin = chrome.newTab(url("/"));
        signIn(admin, ADMIN_EMAIL, ADMIN_PASSWORD);
        admin.navigate(url("/admin/instructors"));
        admin.waitFor("[...document.querySelectorAll('#instructorsBody tr')].some(r => r.textContent.includes('" + email + "'))");
        String mine = "[...document.querySelectorAll('#instructorsBody tr')].find(r => r.textContent.includes('" + email + "'))";
        assertThat(admin.text(mine + ".textContent")).contains("Teacher Leyla", "Not yet");
        admin.eval("window.confirm = () => true; " + mine + ".querySelector('.btn--primary').click()");
        admin.waitFor(mine + " === undefined");
        assertThat(mailbox.to(email).getLast().subject()).isEqualTo("You're now an Orbiko Instructor");

        // Approved and confirmed: the new instructor gets into the editor.
        jdbc.update("update app_user set email_verified_at = now() where email = ?", email);
        HeadlessChrome.Tab teacher = chrome.newTab(url("/"));
        signIn(teacher, email, PASSWORD);
        teacher.navigate(url("/admin/sessions"));
        teacher.waitFor("document.body.classList.contains('admin-ready') && document.getElementById('sessionsStatus') !== null");
        assertThat(teacher.eval("document.querySelector('[data-admin-nav-link][data-key=instructors]').hidden").asBoolean())
                .isTrue(); // authors don't handle requests
    }

    @Test
    void forgotPasswordSendsALinkThatSetsANewPassword() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        String email = registerLearner(tab, "Forgetful");
        tab.navigate(url("/login"));
        tab.eval("document.querySelector('.auth-forgot a').click()");
        tab.waitFor("location.pathname === '/forgot-password' && document.readyState === 'complete'");
        tab.eval(type("#email", email));
        tab.eval("document.querySelector('[data-forgot-form] button[type=submit]').click()");
        tab.waitFor("!document.querySelector('[data-forgot-done]').hidden");

        String token = mailbox.latestToken(email, "/reset-password").orElseThrow();
        tab.navigate(url("/reset-password?token=" + token));
        tab.eval(type("#password", "short"));
        tab.eval("document.querySelector('[data-reset-form] button[type=submit]').click()");
        tab.waitFor("document.querySelector('[data-error-for=password]').textContent !== ''");

        tab.eval(type("#password", "my new long password"));
        tab.eval("document.querySelector('[data-reset-form] button[type=submit]').click()");
        tab.waitFor("!document.querySelector('[data-reset-done]').hidden");
        signIn(tab, email, "my new long password");
        tab.waitFor(signedInNavbar("Forgetful"));

        // The link worked once.
        tab.navigate(url("/reset-password?token=" + token));
        tab.eval(type("#password", "yet another password"));
        tab.eval("document.querySelector('[data-reset-form] button[type=submit]').click()");
        tab.waitFor("!document.querySelector('[data-form-error]').hidden");
        assertThat(tab.text("document.querySelector('[data-form-error]').textContent")).contains("invalid or has expired");
    }
}
