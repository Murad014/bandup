package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The grammar course page (/grammar) in Chrome against the real API and the V10 seed lessons. */
class GrammarBrowserTest extends BrowserTestBase {

    private static final String B1_SEED = "b1-present-perfect-past-simple-1";

    @Test
    void theHomePageAndTheNavbarLeadToGrammar() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        assertThat(tab.text("document.querySelector('a[data-skill=grammar]').getAttribute('href')")).isEqualTo("/grammar");
        assertThat(tab.text("[...document.querySelectorAll('a[data-nav-link]')].map(a => a.getAttribute('href')).join(' ')"))
                .contains("/reading", "/grammar");
        tab.eval("document.querySelector('a[data-skill=grammar]').click()");
        tab.waitFor("location.pathname === '/grammar' && !document.getElementById('levelPick').hidden");
        assertThat(tab.eval("document.querySelector('a[data-nav-link][href=\"/grammar\"]').getAttribute('aria-current')")
                .asString()).isEqualTo("page");
    }

    @Test
    void aGuestChoosesALevelSeesTheCourseAndTheLevelIsKeptOnThisDevice() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/grammar"));
        tab.waitFor("document.querySelectorAll('#levelList .g-level').length === 4");
        assertThat(tab.text("[...document.querySelectorAll('#levelList .g-level')].map(b => b.dataset.level).join(' ')"))
                .isEqualTo("A2 B1 B2 C1");
        assertThat(tab.text("document.querySelector('.g-level[data-level=C1]').textContent")).contains("Advanced", "lesson");

        tab.eval("document.querySelector('.g-level[data-level=B1]').click()");
        tab.waitFor("!document.getElementById('course').hidden && document.querySelector('.g-day') !== null");
        assertThat(tab.text("document.getElementById('courseTitle').textContent")).isEqualTo("B1 · Intermediate grammar");
        assertThat(tab.text("document.querySelector('.g-day').dataset.state")).isEqualTo("NEXT");
        assertThat(tab.text("document.querySelector('.g-day').dataset.slug")).isEqualTo(B1_SEED);
        assertThat(tab.text("document.getElementById('todayTitle').textContent")).isEqualTo("Present Perfect vs Past Simple");
        assertThat(tab.text("document.getElementById('todayGo').getAttribute('href')")).isEqualTo("/grammar/lessons/" + B1_SEED);
        assertThat(tab.eval("document.getElementById('guestCard').hidden").asBoolean()).isFalse();
        assertThat(tab.eval("document.getElementById('reviewCard').hidden").asBoolean()).isTrue();

        tab.navigate(url("/grammar"));
        tab.waitFor("!document.getElementById('course').hidden && document.querySelector('.g-day') !== null");
        assertThat(tab.text("document.getElementById('courseTitle').textContent")).startsWith("B1");

        tab.eval("document.getElementById('changeLevel').click()");
        tab.waitFor("!document.getElementById('levelPick').hidden");
        assertThat(tab.eval("document.querySelector('.g-level[data-level=B1]').classList.contains('is-current')").asBoolean())
                .isTrue();
    }

    @Test
    void aSignedInLearnersLevelIsSavedOnTheServerAndTheyGetTheReviewCard() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        String email = registerLearner(tab, "Lale");
        signIn(tab, email, PASSWORD);
        tab.navigate(url("/grammar"));
        tab.waitFor("!document.getElementById('levelPick').hidden");

        tab.eval("document.querySelector('.g-level[data-level=C1]').click()");
        tab.waitFor("!document.getElementById('course').hidden && document.querySelector('.g-day') !== null");
        assertThat(jdbc.queryForObject("""
                select p.level from grammar_progress p join app_user u on u.id = p.user_id where u.email = ?""",
                String.class, email)).isEqualTo("C1");
        assertThat(tab.eval("document.getElementById('guestCard').hidden").asBoolean()).isTrue();
        assertThat(tab.eval("document.getElementById('reviewCard').hidden").asBoolean()).isFalse();
        assertThat(tab.text("document.getElementById('reviewSub').textContent")).contains("Finish a lesson");
        assertThat(tab.text("document.querySelector('.g-day').dataset.slug")).isEqualTo("c1-inversion-1");
    }

    @Test
    void thePageFitsAPhoneScreen() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        tab.call("Emulation.setDeviceMetricsOverride", tools.jackson.databind.json.JsonMapper.builder().build()
                .createObjectNode().put("width", 375).put("height", 740).put("deviceScaleFactor", 2).put("mobile", true));
        tab.navigate(url("/grammar"));
        tab.waitFor("document.querySelectorAll('.g-level').length === 4");
        assertThat(tab.eval("document.documentElement.scrollWidth <= window.innerWidth + 1").asBoolean()).isTrue();
        tab.screenshot(java.nio.file.Path.of("build/screenshots/grammar-levels-phone.png"));

        tab.navigate(url("/grammar?level=B1"));
        tab.waitFor("document.querySelector('.g-day') !== null");
        assertThat(tab.eval("document.documentElement.scrollWidth <= window.innerWidth + 1").asBoolean()).isTrue();
        tab.screenshot(java.nio.file.Path.of("build/screenshots/grammar-course-phone.png"));
    }

    @Test
    void desktopScreenshotsInBothThemes() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/grammar?level=B1"));
        tab.waitFor("document.querySelector('.g-day') !== null");
        tab.screenshot(java.nio.file.Path.of("build/screenshots/grammar-course.png"));
        tab.eval("document.documentElement.dataset.theme = 'dark'");
        tab.screenshot(java.nio.file.Path.of("build/screenshots/grammar-course-dark.png"));
    }
}
