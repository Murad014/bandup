package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The admin panel on the admin API in Chrome (frontend admin tasks 1–7).
 * Every test works on its own new exercise, so the seeded Wolves passage that
 * other browser tests read is never changed.
 */
class AdminBrowserTest extends BrowserTestBase {

    @Test
    void theListShowsExercisesFromTheApi() throws Exception {
        HeadlessChrome.Tab tab = adminTab();
        tab.navigate(url("/admin/sessions"));
        tab.waitFor("document.querySelector('tr[data-id=\"yellowstone-wolves\"]') !== null");

        String row = "document.querySelector('tr[data-id=\"yellowstone-wolves\"]')";
        assertThat(tab.text(row + ".children[1].textContent")).isEqualTo("Stage 1 · Passage 4");
        assertThat(tab.text(row + ".querySelector('.badge').textContent")).isEqualTo("published");
        assertThat(tab.text(row + ".children[3].textContent")).isEqualTo("6");
        assertThat(tab.eval("fetch('/js/admin/fixtures.js').then(r => r.status)").asInt()).isEqualTo(404);
    }

    @Test
    void theEditorLoadsTheFullExercise() throws Exception {
        HeadlessChrome.Tab tab = adminTab();
        tab.navigate(url("/admin/sessions/yellowstone-wolves/edit"));
        tab.waitFor("document.querySelectorAll('.gloss-row').length === 25");

        assertThat(tab.text("document.getElementById('title').value")).isEqualTo("The Return of the Wolves");
        assertThat(tab.text("document.getElementById('statusBadge').textContent")).isEqualTo("published");
        assertThat(tab.text(field("glosses.g1.meanings.az") + ".querySelector('input').value")).isEqualTo("məhv edilmək");
        assertThat(tab.text(field("glosses.g1.meanings.en") + ".querySelector('.machine').textContent")).contains("Machine-written");
        assertThat(tab.text(field("questionGroups[0].questions[0].answer") + ".querySelector('select').value")).isEqualTo("FALSE");
        assertThat(tab.text(field("questionGroups[1].questions[0].answer") + ".querySelector('input').value")).isEqualTo("riverbanks");
        assertThat(tab.text("document.querySelector('.q-num').textContent")).isEqualTo("Q1");
        assertThat(tab.eval("window.__adminEditor.isDirty()").asBoolean()).isFalse();
        tab.screenshot(java.nio.file.Path.of("build/screenshots/admin-editor-wolves.png"));
        // Loading and saving back unchanged sends the same content (ids kept).
        assertThat(tab.text("JSON.stringify(window.__adminEditor._toInput().questionGroups.map(g => g.questions.map(q => q.id)))"))
                .doesNotContain("null");
    }

    @Test
    void anAuthorWritesAndPublishesANewExerciseThatLearnersCanOpen() throws Exception {
        String slug = "ui-made-" + UUID.randomUUID().toString().substring(0, 8);
        HeadlessChrome.Tab tab = adminTab();
        tab.navigate(url("/admin/sessions/new"));
        tab.waitFor("document.body.classList.contains('admin-ready') && document.getElementById('slugField') && !document.getElementById('slugField').hidden");

        tab.eval(type("#slug", slug));
        tab.eval(type("#title", "Made in the editor"));
        tab.eval(passage("<p><span class=\"ev\" id=\"e1\">Beavers built dams on the river.</span></p>"));
        tab.eval(select("Beavers"));
        tab.eval("document.getElementById('glossBtn').click()");
        tab.waitFor("document.querySelector('.gloss-row') !== null");
        tab.eval(type(fieldSelector("glosses.g1.meanings.az") + " input", "qunduzlar"));

        tab.eval(button("+ Add question group"));
        tab.waitFor(field("questionGroups[0].questions[0].stem") + " !== null");
        assertThat(tab.text("document.getElementById('publishHints').textContent")).contains("Q1 must be one of TRUE / FALSE / NOT GIVEN.");
        tab.eval(type(fieldSelector("questionGroups[0].instruction") + " input", "Do the statements agree with the text?"));
        tab.eval(type(fieldSelector("questionGroups[0].questions[0].stem") + " input", "Beavers built dams."));
        tab.eval(choose(fieldSelector("questionGroups[0].questions[0].answer") + " select", "TRUE"));
        tab.eval(type(fieldSelector("questionGroups[0].questions[0].evidenceId") + " input", "e1"));
        tab.waitFor("document.getElementById('publishHints').hidden");

        tab.eval("document.getElementById('saveBtn').click()");
        tab.waitFor("location.pathname === '/admin/sessions/" + slug + "/edit'");
        tab.waitFor("document.getElementById('revisionInfo').textContent === 'Revision 1 · version 0'");
        assertThat(tab.eval("window.__adminEditor.isDirty()").asBoolean()).isFalse();
        assertThat(tab.text("document.querySelector('.q-num').textContent")).isEqualTo("Q1"); // has its id now

        tab.eval("document.getElementById('publishBtn').click()");
        tab.waitFor("document.getElementById('statusBadge').textContent === 'published'");

        HeadlessChrome.Tab learner = chrome.newTab(url("/reading/" + slug + "?x"));
        learner.eval("localStorage.setItem('bandup.helpLanguage', 'az')");
        learner.navigate(url("/reading/" + slug));
        learner.waitFor("document.querySelectorAll('#questions .q').length === 1");
        learner.eval("document.querySelector('#passage .w[data-g=g1]').click()");
        assertThat(learner.text("document.getElementById('pop').textContent")).isEqualTo("Beaversqunduzlar");
    }

    @Test
    void publishIsBlockedWithErrorsAtTheirFields() throws Exception {
        String slug = create("blocked", "[]", false);
        HeadlessChrome.Tab tab = adminTab();
        tab.navigate(url("/admin/sessions/" + slug + "/edit"));
        tab.waitFor(field("questionGroups[0].questions[0].answer") + " !== null");

        tab.eval("document.getElementById('publishBtn').click()");

        tab.waitFor("!document.getElementById('editorErrors').hidden");
        assertThat(tab.text("document.getElementById('editorErrors').textContent")).contains("Q1 must be one of TRUE / FALSE / NOT GIVEN.");
        assertThat(tab.eval(field("questionGroups[0].questions[0].answer") + ".classList.contains('has-error')").asBoolean()).isTrue();
        assertThat(tab.text(field("questionGroups[0].questions[0].answer") + ".querySelector('.field-err').textContent"))
                .isEqualTo("Q1 must be one of TRUE / FALSE / NOT GIVEN.");
        assertThat(tab.text("document.getElementById('statusBadge').textContent")).isEqualTo("draft");
        tab.screenshot(java.nio.file.Path.of("build/screenshots/admin-editor-publish-blocked.png"));
    }

    @Test
    void aStaleSaveOffersAReload() throws Exception {
        String slug = create("stale", "['TRUE']", false);
        HeadlessChrome.Tab first = adminTab();
        first.navigate(url("/admin/sessions/" + slug + "/edit"));
        HeadlessChrome.Tab second = chrome.newTab(url("/admin/sessions/" + slug + "/edit"));
        first.waitFor("document.getElementById('title').value !== ''");
        second.waitFor("document.getElementById('title').value !== ''");

        first.eval(type("#title", "First author title"));
        first.eval("document.getElementById('saveBtn').click()");
        first.waitFor("document.getElementById('revisionInfo').textContent.endsWith('version 1')");

        second.eval(type("#title", "Second author title"));
        second.eval("document.getElementById('saveBtn').click()");
        second.waitFor("!document.getElementById('editorNotice').hidden");
        assertThat(second.text("document.getElementById('editorNotice').textContent")).contains("Someone else changed this exercise");
        assertThat(second.eval("document.getElementById('editorReload') !== null").asBoolean()).isTrue();
        assertThat(second.text("document.getElementById('title').value")).isEqualTo("Second author title"); // nothing lost yet
    }

    @Test
    void savingAPublishedExerciseWarnsAndRaisesTheRevision() throws Exception {
        String slug = create("published", "['TRUE']", true);
        HeadlessChrome.Tab tab = adminTab();
        tab.navigate(url("/admin/sessions/" + slug + "/edit"));
        tab.waitFor("document.getElementById('statusBadge').textContent === 'published'");
        tab.eval("window.__confirmed = []; window.confirm = (m) => { window.__confirmed.push(m); return true; }");

        tab.eval(type("#title", "Edited while published"));
        tab.eval("document.getElementById('saveBtn').click()");

        tab.waitFor("document.getElementById('revisionInfo').textContent.startsWith('Revision 2')");
        assertThat(tab.text("window.__confirmed[0]")).contains("Saving creates revision 2");
    }

    @Test
    void deletingAnExerciseWithAttemptsArchivesIt() throws Exception {
        String slug = create("archive-me", "['TRUE']", true);
        HeadlessChrome.Tab tab = adminTab();
        tab.eval("fetch('/api/v1/attempts', {method: 'POST', headers: {'Content-Type': 'application/json'},"
                + " body: JSON.stringify({passageId: '" + slug + "', elapsedSeconds: 3, answers: []})}).then(r => r.status)");
        tab.navigate(url("/admin/sessions"));
        tab.waitFor("document.querySelector('tr[data-id=\"" + slug + "\"]') !== null");
        tab.eval("window.confirm = () => true");

        tab.eval("document.querySelector('tr[data-id=\"" + slug + "\"] button[data-action=delete]').click()");

        tab.waitFor("document.getElementById('sessionsNote').textContent.includes('archived instead of deleted')");
        assertThat(tab.text("document.querySelector('tr[data-id=\"" + slug + "\"] .badge').textContent")).isEqualTo("archived");
        assertThat(tab.eval("document.querySelector('tr[data-id=\"" + slug + "\"] a.btn')").isNull()).isTrue(); // no Edit
    }

    @Test
    void unsavedWorkSurvivesBeingSignedOut() throws Exception {
        String slug = create("signed-out", "['TRUE']", false);
        HeadlessChrome.Tab tab = adminTab();
        tab.navigate(url("/admin/sessions/" + slug + "/edit"));
        tab.waitFor("document.getElementById('title').value !== ''");
        tab.eval(type("#title", "Written before signing out"));

        HeadlessChrome.Tab other = chrome.newTab(url("/"));
        other.eval(AUTH + ".then(m => m.logout())");
        Thread.sleep(500); // the sign-out reaches the editor tab; it stays because of the unsaved work
        assertThat(tab.text("location.pathname")).isEqualTo("/admin/sessions/" + slug + "/edit");

        tab.eval("document.getElementById('saveBtn').click()");
        tab.waitFor("location.pathname === '/login'");
        tab.eval(type("#email", ADMIN_EMAIL));
        tab.eval(type("#password", ADMIN_PASSWORD));
        tab.eval("document.querySelector('[data-login-form] button[type=submit]').click()");
        tab.waitFor("location.pathname === '/admin/sessions/" + slug + "/edit' && !document.getElementById('editorNotice').hidden");

        tab.eval(button("Restore them"));
        assertThat(tab.text("document.getElementById('title').value")).isEqualTo("Written before signing out");
        assertThat(tab.eval("window.__adminEditor.isDirty()").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------ helpers

    private HeadlessChrome.Tab adminTab() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);
        return tab;
    }

    /** Creates (and optionally publishes) a one-question TFNG exercise through the admin API; returns its slug. */
    private String create(String name, String answer, boolean publish) throws Exception {
        String slug = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        HeadlessChrome.Tab tab = adminTab();
        String body = "{id: '" + slug + "', title: 'Exercise " + name + "', format: 'MINI',"
                + " passageHtml: '<p><span class=\"ev\" id=\"e1\">Wolves came back.</span></p>',"
                + " questionGroups: [{type: 'TFNG', instruction: 'True or false?', questions: [{text: 'Wolves came back.', answer: " + answer + "}]}]}";
        tab.eval(AUTH + ".then(m => m.apiFetch('/api/v1/admin/passages', {method: 'POST', json: " + body + "}))"
                + (publish ? ".then(() => import('/js/auth.js')).then(m => m.apiFetch('/api/v1/admin/passages/" + slug + "/publish', {method: 'POST'}))" : ""));
        return slug;
    }

    private static String fieldSelector(String path) {
        return "[data-field=\"" + path + "\"]";
    }

    private static String field(String path) {
        return "document.querySelector('" + fieldSelector(path) + "')";
    }

    private static String passage(String html) {
        return "(() => { const e = document.getElementById('passageEdit'); e.innerHTML = '" + html
                + "'; e.dispatchEvent(new Event('input')); })()";
    }

    /** Selects the first occurrence of {@code word} in the passage editor. */
    private static String select(String word) {
        return "(() => { const w = document.createTreeWalker(document.getElementById('passageEdit'), NodeFilter.SHOW_TEXT); let n;"
                + " while ((n = w.nextNode())) { const i = n.data.indexOf('" + word + "'); if (i >= 0) {"
                + " const r = document.createRange(); r.setStart(n, i); r.setEnd(n, i + " + word.length() + ");"
                + " const s = getSelection(); s.removeAllRanges(); s.addRange(r); return true; } } return false; })()";
    }

    private static String choose(String selector, String value) {
        return "(() => { const s = document.querySelector('" + selector + "'); s.value = '" + value
                + "'; s.dispatchEvent(new Event('change', {bubbles: true})); })()";
    }

    private static String button(String text) {
        return "[...document.querySelectorAll('button')].find(b => b.textContent === '" + text + "').click()";
    }
}
