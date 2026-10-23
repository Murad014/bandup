package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * auth.js and the navbar in a real (headless) Chrome against the real app
 * (frontend task 2). Each test gets a fresh browser, so cookies don't leak
 * between tests. Skipped where Chrome isn't installed.
 */
class AuthBrowserTest extends BrowserTestBase {

    @Test
    void anonymousVisitorsSeeSignIn() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/reading/yellowstone-wolves"));
        tab.waitFor(signedOutNavbar());

        assertThat(tab.text("document.querySelector('[data-nav-signin] a').getAttribute('href')"))
                .isEqualTo("/login?next=%2Freading%2Fyellowstone-wolves");
        assertThat(tab.eval("document.querySelector('[data-nav-admin]').hidden").asBoolean()).isTrue();
    }

    @Test
    void aLearnerSignsInStaysSignedInAfterReloadAndTheTokenIsNeverStored() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        String email = registerLearner(tab, "Aysel");

        signIn(tab, email, PASSWORD);
        tab.waitFor(signedInNavbar("Aysel"));
        assertThat(tab.eval("document.querySelector('[data-nav-admin]').hidden").asBoolean()).isTrue();

        // Memory only: nothing in storage, and the refresh cookie is HttpOnly (invisible to scripts).
        assertThat(tab.eval("localStorage.length + sessionStorage.length").asInt()).isZero();
        assertThat(tab.text("document.cookie")).doesNotContain("bandup_refresh");

        tab.navigate(url("/"));
        tab.waitFor(signedInNavbar("Aysel"));
        assertThat(tab.text(AUTH + ".then(m => m.apiFetch('/api/v1/auth/me')).then(u => u.displayName)")).isEqualTo("Aysel");
    }

    @Test
    void anAdminSeesTheAdminLink() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));

        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);

        tab.waitFor("!document.querySelector('[data-nav-admin]').hidden");
    }

    @Test
    void twoTabsReloadingAtTheSameTimeBothStaySignedIn() throws Exception {
        HeadlessChrome.Tab first = chrome.newTab(url("/"));
        String email = registerLearner(first, "Leyla");
        signIn(first, email, PASSWORD);
        HeadlessChrome.Tab second = chrome.newTab(url("/"));
        second.waitFor(signedInNavbar("Leyla"));

        for (int round = 0; round < 3; round++) {
            // Both tabs refresh with the same cookie at once; without the lock the server
            // would see a reused refresh token and end the session.
            CompletableFuture<Void> a = CompletableFuture.runAsync(() -> reload(first));
            CompletableFuture<Void> b = CompletableFuture.runAsync(() -> reload(second));
            CompletableFuture.allOf(a, b).join();
            first.waitFor(signedInNavbar("Leyla"));
            second.waitFor(signedInNavbar("Leyla"));
        }
        assertThat(first.text(AUTH + ".then(m => m.apiFetch('/api/v1/auth/me')).then(u => u.displayName)")).isEqualTo("Leyla");
    }

    @Test
    void signingOutEndsTheSessionInEveryTab() throws Exception {
        HeadlessChrome.Tab first = chrome.newTab(url("/"));
        String email = registerLearner(first, "Kamran");
        signIn(first, email, PASSWORD);
        HeadlessChrome.Tab second = chrome.newTab(url("/"));
        second.waitFor(signedInNavbar("Kamran"));

        first.eval("document.querySelector('[data-nav-signout]').click()");

        first.waitFor(signedOutNavbar());
        second.waitFor(signedOutNavbar());
        second.navigate(url("/"));
        second.waitFor(signedOutNavbar());
    }

    @Test
    void aWrongPasswordIsOneMessageAndOnlySameSiteNextIsAllowed() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));

        assertThat(tab.text(AUTH + ".then(m => m.login('nobody@bandup.test', 'wrong-password-1').then(() => 'ok', e => e.status + ' ' + e.message))"))
                .isEqualTo("401 Wrong email or password.");
        assertThat(tab.text(AUTH + ".then(m => ['/admin', '//evil.com', 'https://evil.com', '/\\\\evil', 'admin', null]"
                + ".map(v => m.safeNext(v)).join(' '))")).isEqualTo("/admin / / / / /");
    }

    // ------------------------------------------------------------ helpers

    private void reload(HeadlessChrome.Tab tab) {
        try {
            tab.navigate(url("/"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
