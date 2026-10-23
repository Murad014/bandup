package com.bandup.browser;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.bandup.TestcontainersConfiguration;

/**
 * The real app on a random port + a fresh headless Chrome per test (cookies
 * don't leak between tests). Every browser test shares this setup, so they
 * share one application context. Skipped where Chrome isn't installed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bandup.auth.admin-email=" + BrowserTestBase.ADMIN_EMAIL,
        "bandup.auth.admin-password=" + BrowserTestBase.ADMIN_PASSWORD})
@Import(TestcontainersConfiguration.class)
abstract class BrowserTestBase {

    static final String ADMIN_EMAIL = "admin@bandup.test";
    static final String ADMIN_PASSWORD = "admin-password-123";
    static final String PASSWORD = "correct horse battery";
    static final String AUTH = "import('/js/auth.js')";

    @LocalServerPort
    int port;

    HeadlessChrome chrome;

    @BeforeAll
    static void needsChrome() {
        assumeTrue(HeadlessChrome.find() != null, "Chrome isn't installed");
    }

    @BeforeEach
    void startBrowser() throws Exception {
        chrome = HeadlessChrome.start();
    }

    @AfterEach
    void stopBrowser() {
        chrome.close();
    }

    String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Registers a learner through auth.js (not signed in); returns the email. */
    String registerLearner(HeadlessChrome.Tab tab, String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@bandup.test";
        tab.eval(AUTH + ".then(m => m.register({email: '" + email + "', password: '" + PASSWORD + "', displayName: '"
                + name + "', uiLanguage: 'en', supportLanguage: 'az'}))");
        return email;
    }

    void signIn(HeadlessChrome.Tab tab, String email, String password) throws Exception {
        tab.eval(AUTH + ".then(m => m.login('" + email + "', '" + password + "'))");
    }

    static String signedInNavbar(String name) {
        return "document.querySelector('[data-nav-signin]').hidden"
                + " && !document.querySelector('[data-nav-account]').hidden"
                + " && document.querySelector('[data-nav-username]').textContent === '" + name + "'";
    }

    static String signedOutNavbar() {
        return "!document.querySelector('[data-nav-signin]').hidden && document.querySelector('[data-nav-account]').hidden";
    }

    /** Sets an input's value the way typing does (so listeners see it). */
    static String type(String selector, String value) {
        return "(() => { const el = document.querySelector('" + selector + "'); el.value = '" + value
                + "'; el.dispatchEvent(new Event('input', {bubbles: true})); })()";
    }
}
