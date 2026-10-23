package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The admin pages check the account in the browser (frontend task 4). */
class AdminGuardBrowserTest extends BrowserTestBase {

    @Test
    void signedOutVisitorsAreSentToSignInAndComeBackAfterwards() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));

        tab.open(url("/admin/sessions")); // redirects to sign-in
        tab.waitFor("location.pathname === '/login' && document.readyState === 'complete'");
        assertThat(tab.text("location.search")).isEqualTo("?next=%2Fadmin%2Fsessions");

        tab.eval(type("#email", ADMIN_EMAIL));
        tab.eval(type("#password", ADMIN_PASSWORD));
        tab.eval("document.querySelector('[data-login-form] button[type=submit]').click()");
        tab.waitFor("location.pathname === '/admin/sessions' && document.body.classList.contains('admin-ready')");
        assertThat(tab.text("document.querySelector('[data-admin-username]').textContent")).isNotEmpty();
        assertThat(tab.eval("document.querySelector('[data-admin-account]').hidden").asBoolean()).isFalse();
    }

    @Test
    void aLearnerSeesNoAccessAndNoAdminUi() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, registerLearner(tab, "Learner"), PASSWORD);

        tab.navigate(url("/admin/sessions"));

        tab.waitFor("document.querySelector('[data-admin-noaccess]') !== null");
        assertThat(tab.text("document.querySelector('.admin-main').textContent")).contains("You don't have access");
        assertThat(tab.eval("document.querySelector('#sessionsRoot')").isNull()).isTrue();
    }

    @Test
    void theAdminContentIsHiddenUntilTheCheckPasses() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);

        tab.navigate(url("/admin"));
        tab.waitFor("document.body.classList.contains('admin-ready')");
        assertThat(tab.text("getComputedStyle(document.querySelector('.admin-main')).visibility")).isEqualTo("visible");

        tab.eval("document.body.classList.remove('admin-ready')");
        assertThat(tab.text("getComputedStyle(document.querySelector('.admin-main')).visibility")).isEqualTo("hidden");
    }

    @Test
    void signingOutFromTheSidebarLeavesTheAdminArea() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);
        tab.navigate(url("/admin"));
        tab.waitFor("document.body.classList.contains('admin-ready')");

        tab.eval("document.querySelector('[data-admin-signout]').click()");

        tab.waitFor("location.pathname === '/' && document.readyState === 'complete'");
        tab.waitFor(signedOutNavbar());
    }
}
