package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bandup.api.vocabulary.StubOpenRouter;

import tools.jackson.databind.node.ObjectNode;

/**
 * Admin → Vocabulary in Chrome (vocabulary requirement 8): the list with its
 * filters, an edit with errors at their fields, a fixed card seen by
 * learners, and the delete rule. No AI key here, so regenerate says so.
 */
class AdminVocabularyBrowserTest extends BrowserTestBase {

    @Test
    void anAdminFindsFixesAndDeletesWordCards() throws Exception {
        long predator = bank("predator");
        long erosion = bank("erosion");
        jdbc.update("""
                insert into vocab_saved_word (id, user_id, word_id, state, added_on)
                select gen_random_uuid(), id, ?, 'NEW', current_date from app_user where email = ?""", predator, ADMIN_EMAIL);

        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);
        tab.navigate(url("/admin/vocabulary"));
        tab.waitFor("document.querySelector('#vcBody tr[data-id=\"" + predator + "\"]') !== null");
        assertThat(tab.text("document.querySelector('[data-admin-nav-link][aria-current=\"page\"]').textContent")).isEqualTo("Vocabulary");
        assertThat(tab.text("document.querySelector('#vcBody tr[data-id=\"" + predator + "\"]').textContent"))
                .contains("predator", "ready", "1 learner", "AI");
        assertThat(tab.eval("document.querySelector('#vcBody tr[data-id=\"" + predator + "\"] [data-action=\"delete\"]') === null")
                .asBoolean()).as("a saved word can't be deleted").isTrue();
        tab.eval("(() => { const s = document.getElementById('vcSearch'); s.value = 'eros'; s.dispatchEvent(new Event('input')); })()");
        tab.waitFor("document.querySelectorAll('#vcBody tr[data-id]').length === 1");
        shot(tab, "list");

        tab.navigate(url("/admin/vocabulary/" + predator + "/edit"));
        tab.waitFor("document.getElementById('lemma').value === 'predator'");
        tab.eval("(() => { document.getElementById('ipa').value = 'predator'; document.getElementById('meaningAz').value = '';"
                + " document.getElementById('vcForm').dispatchEvent(new Event('input')); })()");
        tab.eval("document.getElementById('vcSave').click()");
        tab.waitFor("document.getElementById('vcErrorList') !== null");
        assertThat(tab.text("document.getElementById('vcErrorList').textContent")).contains("ipa —", "meaning.az —");
        assertThat(tab.eval("document.querySelector('[data-field=\"ipa\"]').classList.contains('has-error')").asBoolean()).isTrue();
        shot(tab, "errors");

        tab.eval("(() => { document.getElementById('ipa').value = '/ˈpredətə/'; document.getElementById('meaningAz').value = 'yırtıcı heyvan';"
                + " document.getElementById('vcForm').dispatchEvent(new Event('input')); })()");
        tab.eval("document.getElementById('vcSave').click()");
        tab.waitFor("document.getElementById('vcNotice').textContent.startsWith('Saved')");
        assertThat(tab.text("document.getElementById('vcInfo').textContent")).contains("revision 2", "an admin");
        assertThat(tab.text("fetch('/api/v1/vocabulary/lookup?text=predators').then(r => r.json()).then(j => j.meaning.az)"))
                .isEqualTo("yırtıcı heyvan");

        tab.eval("document.getElementById('vcRegenerate').click()");
        tab.waitFor("!document.getElementById('vcErrors').hidden");
        assertThat(tab.text("document.getElementById('vcErrors').textContent")).contains("BANDUP_OPENROUTER_API_KEY");

        tab.navigate(url("/admin/vocabulary"));
        tab.waitFor("document.querySelector('#vcBody tr[data-id=\"" + erosion + "\"] [data-action=\"delete\"]') !== null");
        tab.eval("window.confirm = () => true");
        tab.eval("document.querySelector('#vcBody tr[data-id=\"" + erosion + "\"] [data-action=\"delete\"]').click()");
        tab.waitFor("document.querySelector('#vcBody tr[data-id=\"" + erosion + "\"]') === null");
    }

    private long bank(String w) {
        ObjectNode card = StubOpenRouter.card(w, List.of(w + "s"));
        card.putObject("meaning").put("en", "the test meaning for this word").put("az", "az " + w);
        Long id = jdbc.queryForObject("""
                insert into vocab_word (lemma, status, source, revision, content) values (?, 'READY', 'AI', 1, cast(? as jsonb))
                returning id""", Long.class, w, card.toString());
        jdbc.update("insert into vocab_word_form (form, word_id) values (?, ?), (?, ?)", w, id, w + "s", id);
        return id;
    }

    private static void shot(HeadlessChrome.Tab tab, String name) throws Exception {
        tab.screenshot(Path.of("build/screenshots/admin-vocabulary-" + name + ".png"));
    }
}
