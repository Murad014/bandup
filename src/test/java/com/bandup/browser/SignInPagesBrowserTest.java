package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The sign-in and sign-up pages in Chrome against the real API (frontend task 3). */
class SignInPagesBrowserTest extends BrowserTestBase {

    @org.springframework.beans.factory.annotation.Autowired com.bandup.RecordingMailer mailbox;

    @Test
    void signUpAsksToConfirmTheEmailAndTheLinkActivatesTheAccount() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@bandup.test";
        HeadlessChrome.Tab tab = chrome.newTab(url("/register"));

        tab.eval(type("#displayName", "Nigar"));
        tab.eval(type("#email", email));
        tab.eval(type("#password", PASSWORD));
        tab.eval("document.querySelector('#supportLanguage').value = 'az'");
        tab.eval("document.querySelector('[data-register-form] button[type=submit]').click()");

        tab.waitFor("!document.querySelector('[data-check-email]').hidden");
        assertThat(tab.eval("document.querySelector('[data-register-form]').hidden").asBoolean()).isTrue();
        assertThat(tab.text("document.querySelector('[data-sent-to]').textContent")).isEqualTo(email);
        assertThat(tab.eval("document.querySelector('[data-instructor-note]').hidden").asBoolean()).isTrue();
        assertThat(tab.eval("document.querySelector('[data-nav-signin]').hidden").asBoolean()).isFalse(); // not signed in

        // Signing in before confirming says what to do and offers a new link.
        tab.navigate(url("/login"));
        tab.eval(type("#email", email));
        tab.eval(type("#password", PASSWORD));
        tab.eval("document.querySelector('[data-login-form] button[type=submit]').click()");
        tab.waitFor("!document.querySelector('[data-confirm-email]').hidden");
        assertThat(tab.eval("document.querySelector('[data-form-error]').hidden").asBoolean()).isTrue();

        // The emailed link confirms the account; then signing in works.
        String token = mailbox.latestToken(email, "/verify-email").orElseThrow();
        tab.navigate(url("/verify-email?token=" + token));
        tab.waitFor("document.querySelector('[data-title]').textContent === 'Email confirmed'");
        tab.eval("document.querySelector('[data-sign-in]').click()");
        tab.waitFor("location.pathname === '/login' && document.readyState === 'complete'");
        signIn(tab, email, PASSWORD);
        tab.waitFor(signedInNavbar("Nigar"));
        assertThat(tab.text(AUTH + ".then(m => m.currentUser()).then(u => u.supportLanguage)")).isEqualTo("az");

        // The same link again: it worked once.
        tab.navigate(url("/verify-email?token=" + token));
        tab.waitFor("document.querySelector('[data-title]').textContent === \"This link doesn't work\"");
    }

    @Test
    void signUpShowsEveryFieldErrorAndKeepsTheInput() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/register"));

        tab.eval(type("#displayName", "x".repeat(81)));
        tab.eval(type("#email", "not-an-email"));
        tab.eval(type("#password", "short"));
        tab.eval("document.querySelector('[data-register-form] button[type=submit]').click()");

        tab.waitFor("document.querySelector('[data-error-for=password]').textContent !== ''");
        assertThat(tab.text("document.querySelector('[data-error-for=email]').textContent")).isNotEmpty();
        assertThat(tab.text("document.querySelector('[data-error-for=displayName]').textContent")).isNotEmpty();
        assertThat(tab.eval("document.querySelectorAll('.field.has-error').length").asInt()).isEqualTo(3);
        assertThat(tab.text("document.querySelector('#email').value")).isEqualTo("not-an-email"); // input kept
        assertThat(tab.text("location.pathname")).isEqualTo("/register");
    }

    @Test
    void aTakenEmailIsShownAtTheEmailField() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/register"));
        String email = registerLearner(tab, "Taken");

        tab.eval(type("#email", email));
        tab.eval(type("#password", PASSWORD));
        tab.eval("document.querySelector('[data-register-form] button[type=submit]').click()");

        tab.waitFor("document.querySelector('[data-error-for=email]').textContent.includes('already has an account')");
    }

    @Test
    void signInGoesToNextAndAWrongPasswordGetsOneMessage() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        String email = registerLearner(tab, "Murad");
        tab.navigate(url("/login?next=%2Fadmin"));

        tab.eval(type("#email", email));
        tab.eval(type("#password", "wrong-password-1"));
        tab.eval("document.querySelector('[data-login-form] button[type=submit]').click()");
        tab.waitFor("!document.querySelector('[data-form-error]').hidden");
        assertThat(tab.text("document.querySelector('[data-form-error]').textContent")).isEqualTo("Wrong email or password.");
        assertThat(tab.text("document.querySelector('#password').value")).isEmpty();

        tab.eval(type("#password", PASSWORD));
        tab.eval("document.querySelector('[data-login-form] button[type=submit]').click()");
        tab.waitFor("location.pathname === '/admin' && document.readyState === 'complete'");
    }

    @Test
    void aForeignNextIsIgnoredAndTheLinksKeepNext() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/login?next=https%3A%2F%2Fevil.com"));
        assertThat(tab.text("document.querySelector('[data-keep-next]').getAttribute('href')")).isEqualTo("/register?next=%2F");

        tab.navigate(url("/login?next=%2Freading%2Fyellowstone-wolves"));
        assertThat(tab.text("document.querySelector('[data-keep-next]').getAttribute('href')"))
                .isEqualTo("/register?next=%2Freading%2Fyellowstone-wolves");

        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);
        tab.navigate(url("/login?next=%2F%2Fevil.com"));
        tab.eval(type("#email", ADMIN_EMAIL));
        tab.eval(type("#password", ADMIN_PASSWORD));
        tab.eval("document.querySelector('[data-login-form] button[type=submit]').click()");
        tab.waitFor("location.href === '" + url("/") + "' && document.readyState === 'complete'");
    }
}
