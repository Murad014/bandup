package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The reading catalog (/reading) in Chrome against the real API. */
class CatalogBrowserTest extends BrowserTestBase {

    @Test
    void theNavbarLeadsToTheCatalogAndACardOpensThePassage() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        tab.eval("document.querySelector('a[data-nav-link]').click()");
        tab.waitFor("location.pathname === '/reading' && document.querySelector('.ex-card[data-id=\"yellowstone-wolves\"]') !== null");

        String card = "document.querySelector('.ex-card[data-id=\"yellowstone-wolves\"]')";
        assertThat(tab.text(card + ".querySelector('h2').textContent")).isEqualTo("The Return of the Wolves");
        assertThat(tab.text(card + ".querySelector('.ex-meta').textContent")).contains("B1", "Mini", "5 min", "6 questions");
        assertThat(tab.text(card + ".textContent")).contains("True / False / Not Given · Sentence completion", "Nature");
        assertThat(tab.eval(card + ".querySelector('.ex-progress')").isNull()).isTrue(); // anonymous: no progress
        assertThat(tab.eval("document.getElementById('notDoneLabel').hidden").asBoolean()).isTrue();

        tab.eval(card + ".click()");
        tab.waitFor("location.pathname === '/reading/yellowstone-wolves' && document.querySelectorAll('#questions .q').length === 6");
    }

    @Test
    void filtersNarrowTheListAndLiveInTheUrl() throws Exception {
        String slug = publishB2Exercise();
        HeadlessChrome.Tab tab = chrome.newTab(url("/reading"));
        tab.waitFor("document.querySelector('.ex-card[data-id=\"" + slug + "\"]') !== null");

        tab.eval("document.querySelector('[data-chips=level] input[value=B2]').click()");
        tab.waitFor("location.search === '?level=B2'");
        tab.waitFor("document.querySelector('.ex-card[data-id=\"yellowstone-wolves\"]') === null"
                + " && document.querySelector('.ex-card[data-id=\"" + slug + "\"]') !== null");

        tab.navigate(url("/reading?level=B1&topic=nature"));
        tab.waitFor("document.querySelector('.ex-card[data-id=\"yellowstone-wolves\"]') !== null");
        assertThat(tab.eval("document.querySelector('[data-chips=level] input[value=B1]').checked").asBoolean()).isTrue();
        assertThat(tab.eval("document.querySelector('.ex-card[data-id=\"" + slug + "\"]')").isNull()).isTrue();

        tab.eval("(() => { const s = document.getElementById('timeFilter'); s.value = '5'; s.dispatchEvent(new Event('change', {bubbles: true})); })()");
        tab.waitFor("location.search.includes('maxMinutes=5')");
        tab.eval("document.getElementById('clearFilters').click()");
        tab.waitFor("location.search === '' && document.querySelectorAll('.ex-card').length >= 2");

        tab.navigate(url("/reading?level=C2"));
        tab.waitFor("document.getElementById('catalogStatus').textContent === 'No exercises match these filters.'");
    }

    @Test
    void aSignedInLearnerSeesWhatTheyDidAndCanHideIt() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, registerLearner(tab, "Catalog"), PASSWORD);
        tab.navigate(url("/reading/yellowstone-wolves"));
        tab.waitFor("document.querySelectorAll('#questions .q').length === 6");
        tab.eval("document.getElementById('q1o1').click()"); // FALSE (right)
        tab.eval("document.getElementById('submit').click()");
        tab.waitFor("!document.getElementById('resultBox').hidden");

        tab.navigate(url("/reading"));
        String card = "document.querySelector('.ex-card[data-id=\"yellowstone-wolves\"]')";
        tab.waitFor(card + " !== null && " + card + ".querySelector('.ex-progress') !== null");
        assertThat(tab.text(card + ".querySelector('.ex-progress').textContent")).isEqualTo("✓ Done · best 1/6");

        tab.eval("document.getElementById('notDoneFilter').click()");
        tab.waitFor("location.search === '?notDone=true' && " + card + " === null");
    }

    /** A published B2 exercise on science, made through the admin API. */
    private String publishB2Exercise() throws Exception {
        String slug = "catalog-b2-" + UUID.randomUUID().toString().substring(0, 8);
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);
        tab.eval(AUTH + ".then(m => m.apiFetch('/api/v1/admin/passages', {method: 'POST', json: {id: '" + slug + "',"
                + " title: 'Science at B2', format: 'MINI', level: 'B2', durationSeconds: 900, topicIds: [2],"
                + " passageHtml: '<p>Scientists study wolves.</p>',"
                + " questionGroups: [{type: 'MCQ', instruction: 'Choose one.', questions: [{text: 'Who?', answer: ['A'],"
                + " options: [{key: 'A', label: 'Scientists'}, {key: 'B', label: 'Tourists'}]}]}]}}))"
                + ".then(() => import('/js/auth.js')).then(m => m.apiFetch('/api/v1/admin/passages/" + slug + "/publish', {method: 'POST'}))");
        return slug;
    }
}
