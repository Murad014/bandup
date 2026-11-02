package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Admin → Grammar in Chrome (grammar requirement 12): the list with the seed
 * lessons and its filters, and a lesson's whole way in the editor: bad JSON,
 * server errors with their paths, saved as a draft, preview with answers,
 * a taken position, published at the end (learners see it), unpublished and
 * deleted; unsaved changes ask before leaving.
 */
class AdminGrammarBrowserTest extends BrowserTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void theListShowsTheLessonsAndFilters() throws Exception {
        HeadlessChrome.Tab tab = adminTab("/admin/grammar");
        tab.waitFor("document.querySelector('#grammarBody tr[data-id=\"a2-articles-1\"]') !== null");
        assertThat(tab.text("document.querySelector('[data-admin-nav-link][aria-current=\"page\"]').textContent")).isEqualTo("Grammar");
        for (String slug : new String[] {"a2-articles-1", "b1-present-perfect-past-simple-1", "b2-second-third-conditional-1",
                "c1-inversion-1"}) {
            assertThat(tab.text("document.querySelector('#grammarBody tr[data-id=\"" + slug + "\"] .badge').textContent"))
                    .isEqualTo("published");
        }
        tab.eval("(() => { const s = document.getElementById('grammarLevel'); s.value = 'C1'; s.dispatchEvent(new Event('change')); })()");
        assertThat(tab.eval("[...document.querySelectorAll('#grammarBody tr[data-id]')].every(r => r.children[0].textContent === 'C1')")
                .asBoolean()).isTrue();
        assertThat(tab.text("document.querySelector('#grammarBody tr[data-id=\"c1-inversion-1\"] a.btn--text').getAttribute('href')"))
                .isEqualTo("/grammar/lessons/c1-inversion-1");
        tab.eval("(() => { const s = document.getElementById('grammarSearch'); s.value = 'zzz-nothing'; s.dispatchEvent(new Event('input')); })()");
        assertThat(tab.text("document.getElementById('grammarBody').textContent")).contains("No lessons match.");
        shot(tab, "list");
    }

    @Test
    void anAdminCreatesChecksPublishesAndRemovesALesson() throws Exception {
        String slug = "b1-admin-test-" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode lesson = (ObjectNode) MAPPER.readTree(Files.readString(Path.of("src/test/resources/grammar/b1-lesson.json")));
        lesson.put("id", slug);

        HeadlessChrome.Tab tab = adminTab("/admin/grammar/new");
        tab.waitFor("document.getElementById('gJson').value.includes('b1-topic-name-1')");
        assertThat(tab.text("document.getElementById('gPreview').textContent")).contains("Worked example");

        // Bad JSON: told before anything is sent.
        setJson(tab, "{ \"id\": ");
        tab.waitFor("document.getElementById('gJsonState').textContent.startsWith('JSON error')");
        tab.eval("document.getElementById('gSave').click()");
        assertThat(tab.text("document.getElementById('gErrors').textContent")).startsWith("Fix the JSON first");

        // Missing texts: every server problem with its path.
        setJson(tab, lesson.deepCopy().put("title", "").put("goal", "").toString());
        tab.eval("document.getElementById('gSave').click()");
        tab.waitFor("document.getElementById('gErrorList') !== null");
        String problems = tab.text("document.getElementById('gErrorList').textContent");
        assertThat(problems).contains("title —", "goal —");
        shot(tab, "errors");

        // Fixed: saved as a draft, the address becomes the lesson's.
        setJson(tab, lesson.toString());
        tab.waitFor("document.getElementById('gPreview').textContent.includes('az: Sonda')");
        assertThat(tab.text("document.querySelector('#gPreview [data-item=\"c1\"] li.is-right').textContent")).startsWith("✓ ");
        assertThat(tab.text("document.querySelector('#gPreview [data-item=\"c2\"]').textContent")).contains("Accepted: won");
        assertThat(tab.text("document.getElementById('gPreview').textContent")).contains("az: Sonda");
        tab.eval("document.getElementById('gSave').click()");
        tab.waitFor("document.getElementById('gNotice').textContent === 'Saved as a draft.'");
        assertThat(tab.text("location.pathname")).isEqualTo("/admin/grammar/" + slug + "/edit");
        assertThat(tab.text("document.getElementById('gStatus').textContent")).isEqualTo("draft");
        assertThat(tab.text("document.getElementById('gJson').value")).doesNotContain("\"version\"", "\"status\"");
        shot(tab, "draft");

        // The id can't change.
        tab.eval("(() => { const t = document.getElementById('gJson'); t.value = t.value.replace('\"" + slug + "\"', '\"other-id\"');"
                + " t.dispatchEvent(new Event('input')); })()");
        tab.eval("document.getElementById('gSave').click()");
        assertThat(tab.text("document.getElementById('gErrors').textContent")).contains("The id can't change");
        setJson(tab, lesson.toString());

        // A taken position, then the end of the course.
        tab.eval("document.getElementById('gPosition').value = '1'");
        tab.eval("document.getElementById('gPublish').click()");
        tab.waitFor("!document.getElementById('gErrors').hidden");
        assertThat(tab.text("document.getElementById('gErrors').textContent")).containsIgnoringCase("position");
        tab.eval("document.getElementById('gPosition').value = ''");
        tab.eval("document.getElementById('gPublish').click()");
        tab.waitFor("document.getElementById('gStatus').textContent === 'published'");
        assertThat(tab.text("document.getElementById('gNotice').textContent")).startsWith("Published at position ").endsWith(" of the B1 course.");
        assertThat(tab.eval("document.getElementById('gPublish').hidden && !document.getElementById('gUnpublish').hidden").asBoolean()).isTrue();
        assertThat(tab.eval("document.getElementById('gErrors').hidden").asBoolean()).isTrue();
        assertThat(tab.text("fetch('/api/v1/grammar/course?level=B1&size=100').then(r => r.text())")).contains("\"" + slug + "\"");
        shot(tab, "published");

        tab.eval("document.getElementById('gUnpublish').click()");
        tab.waitFor("document.getElementById('gStatus').textContent === 'draft'");
        assertThat(tab.text("fetch('/api/v1/grammar/course?level=B1&size=100').then(r => r.text())")).doesNotContain(slug);

        // Delete from the list.
        tab.navigate(url("/admin/grammar"));
        tab.waitFor("document.querySelector('#grammarBody tr[data-id=\"" + slug + "\"]') !== null");
        tab.eval("window.confirm = () => true");
        tab.eval("document.querySelector('#grammarBody tr[data-id=\"" + slug + "\"] button[data-action=\"delete\"]').click()");
        tab.waitFor("document.querySelector('#grammarBody tr[data-id=\"" + slug + "\"]') === null"
                + " && document.querySelector('#grammarBody tr[data-id=\"a2-articles-1\"]') !== null");
    }

    @Test
    void unsavedChangesAskBeforeLeaving() throws Exception {
        HeadlessChrome.Tab tab = adminTab("/admin/grammar/a2-articles-1/edit");
        tab.waitFor("document.getElementById('gJson').value.includes('\"a2-articles-1\"')");
        assertThat(tab.text("document.getElementById('gJson').value")).contains("\"accept\"", "\"explanation\"");
        assertThat(tab.text("document.getElementById('gStatus').textContent")).isEqualTo("published");
        tab.eval("(() => { const t = document.getElementById('gJson'); t.value += ' '; t.dispatchEvent(new Event('input')); })()");
        tab.eval("window.confirm = () => false");
        tab.eval("document.querySelector('[data-admin-nav-link][data-key=\"sessions\"]').click()");
        assertThat(tab.text("location.pathname")).isEqualTo("/admin/grammar/a2-articles-1/edit");
    }

    // ------------------------------------------------------------- helpers

    private HeadlessChrome.Tab adminTab(String path) throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);
        tab.navigate(url(path));
        return tab;
    }

    private static void setJson(HeadlessChrome.Tab tab, String value) throws Exception {
        tab.eval("(() => { const t = document.getElementById('gJson'); t.value = " + MAPPER.writeValueAsString(value)
                + "; t.dispatchEvent(new Event('input')); })()");
    }

    private static void shot(HeadlessChrome.Tab tab, String name) throws Exception {
        tab.screenshot(Path.of("build/screenshots/admin-grammar-" + name + ".png"));
    }
}
