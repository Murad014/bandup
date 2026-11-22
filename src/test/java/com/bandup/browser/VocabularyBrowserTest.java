package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.bandup.api.vocabulary.StubOpenRouter;

import tools.jackson.databind.node.ObjectNode;

/**
 * Vocabulary in Chrome (vocabulary requirements 1, 2, 4–6): saving words on
 * the reading page and in My words (a fake OpenRouter writes new cards), a
 * learn session with a wrong task coming back, and the sets with their rounds.
 */
class VocabularyBrowserTest extends BrowserTestBase {

    static final StubOpenRouter AI = start();

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        registry.add("bandup.vocabulary.ai.base-url", AI::url);
        registry.add("bandup.vocabulary.ai.api-key", () -> "browser-test-key");
        registry.add("bandup.vocabulary.retry-every", () -> "1h");
    }

    @AfterAll
    static void stop() {
        AI.stop();
    }

    @Test
    void myWordsAddsAWordThatTurnsReadyAndShowsTheTotals() throws Exception {
        bank("predator", "erosion");
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, registerLearner(tab, "Leyla"), PASSWORD);
        tab.navigate(url("/vocabulary"));
        tab.waitFor("!document.getElementById('vPage').hidden");
        assertThat(tab.text("document.getElementById('vLearnTitle').textContent")).isEqualTo("No new words");

        add(tab, "Predator");
        tab.waitFor("document.querySelector('.v-word[data-word=\"predator\"]') !== null");
        assertThat(tab.text("document.getElementById('vAddMsg').textContent")).isEqualTo("＋ “predator” added.");
        // counted from the server's day, though this browser's clock is on another day (HeadlessChrome.awayFromBaku)
        assertThat(tab.text("document.querySelector('.v-word[data-word=\"predator\"] .v-word__mean').textContent"))
                .contains("added today");
        AI.delay(1500);
        add(tab, "riverbanks");
        tab.waitFor("document.getElementById('vAddMsg').textContent.includes('preparing')");
        assertThat(tab.text("document.querySelector('.v-word[data-word=\"riverbanks\"] .v-state').textContent")).isEqualTo("Preparing… ⏳");
        shot(tab, "preparing");
        tab.waitFor("document.querySelector('.v-word[data-word=\"riverbanks\"] .v-state--new') !== null", java.time.Duration.ofSeconds(15));
        assertThat(tab.text("document.querySelector('.v-word[data-word=\"riverbanks\"] .v-word__mean').textContent"))
                .contains("a thing used in the tests", "test sözü");
        assertThat(AI.calls("riverbanks")).isEqualTo(1);
        assertThat(tab.text("document.getElementById('vNew').textContent")).isEqualTo("2");
        assertThat(tab.text("document.getElementById('vLearnTitle').textContent")).isEqualTo("2 new words ready");
        tab.waitFor("document.querySelector('[data-nav-new-words]').textContent === '2'");

        add(tab, "1234");
        tab.waitFor("document.getElementById('vAddMsg').classList.contains('is-bad')");
        assertThat(tab.text("document.getElementById('vAddMsg').textContent")).contains("English words");

        tab.eval("(() => { const s = document.getElementById('vSearch'); s.value = 'pred'; s.dispatchEvent(new Event('input')); })()");
        tab.waitFor("document.querySelectorAll('.v-word').length === 1");
        tab.eval("(() => { const s = document.getElementById('vSearch'); s.value = ''; s.dispatchEvent(new Event('input')); })()");
        tab.waitFor("document.querySelectorAll('.v-word').length === 2");
        tab.eval("document.querySelector('.v-word[data-word=\"predator\"] .v-remove').click()");
        tab.waitFor("document.querySelectorAll('.v-word').length === 1");
        shot(tab, "words");
        assertThat(tab.eval("document.documentElement.scrollWidth <= window.innerWidth + 1").asBoolean()).isTrue();
    }

    @Test
    void aReaderSavesADottedWordAndASelectedWord() throws Exception {
        bank("predator");
        HeadlessChrome.Tab tab = chrome.newTab(url("/reading/yellowstone-wolves"));
        tab.waitFor("document.querySelector('#passage .w') !== null");
        tab.eval("document.querySelector('#passage .w[data-g=\"g3\"]').click()");
        tab.waitFor("document.querySelector('#pop .pop-save a') !== null");
        assertThat(tab.text("document.querySelector('#pop .pop-save a').textContent")).isEqualTo("Sign in to save words");

        signIn(tab, registerLearner(tab, "Nigar"), PASSWORD);
        tab.navigate(url("/reading/yellowstone-wolves"));
        tab.waitFor("document.querySelector('#passage .w[data-g=\"g3\"]') !== null");
        tab.eval("document.querySelector('#passage .w[data-g=\"g3\"]').click()");
        tab.waitFor("document.querySelector('#pop [data-save-word]') !== null");
        shot(tab, "popover");
        tab.eval("document.querySelector('#pop [data-save-word]').click()");
        tab.waitFor("document.querySelector('#pop .pop-saved') !== null");
        assertThat(tab.eval("document.querySelector('#passage .w[data-g=\"g3\"]').classList.contains('is-saved')").asBoolean()).isTrue();
        tab.waitFor("!document.getElementById('passageWords').hidden");
        assertThat(tab.text("document.getElementById('passageWords').textContent")).contains("New words from this passage · 1", "predator");
        assertThat(AI.calls("predator")).isZero();

        // Select a word that isn't dotted: the bar offers "Add to my words".
        tab.eval("""
                (() => { const w = document.createTreeWalker(document.getElementById('passage'), NodeFilter.SHOW_TEXT); let n;
                  while ((n = w.nextNode())) { const i = n.data.indexOf('hunting'); if (i >= 0) {
                    const r = document.createRange(); r.setStart(n, i); r.setEnd(n, i + 7);
                    getSelection().removeAllRanges(); getSelection().addRange(r);
                    document.dispatchEvent(new MouseEvent('mouseup', {bubbles: true})); return; } } })()""");
        tab.waitFor("!document.getElementById('saveWordBtn').hidden && !document.getElementById('hlBtn').hidden");
        shot(tab, "selection");
        tab.eval("document.getElementById('saveWordBtn').click()");
        tab.waitFor("document.getElementById('passageWords').textContent.includes('· 2')", java.time.Duration.ofSeconds(15));
        assertThat(AI.calls("hunting")).isEqualTo(1);
        assertThat(AI.requests().get(AI.requests().size() - 1).user()).contains("CONTEXT: \"", "hunting");
        tab.waitFor("document.querySelector('#passage .v-saved') !== null");
        assertThat(tab.text("document.querySelector('#passage .v-saved').textContent")).isEqualTo("hunting");
        assertThat(tab.text("document.querySelector('#passageWords a').getAttribute('href')"))
                .isEqualTo("/vocabulary/learn?exercise=yellowstone-wolves");
    }

    @Test
    void aLearnSessionTeachesTheWordsAndAWrongTaskComesBack() throws Exception {
        bank("predator", "erosion", "abundant");
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, registerLearner(tab, "Kamal"), PASSWORD);
        for (String w : List.of("predator", "erosion", "abundant")) {
            tab.eval("import('/js/vocabulary/api.js').then(m => m.save('" + w + "', null))");
        }
        tab.navigate(url("/vocabulary/learn"));
        tab.waitFor("document.getElementById('startLearning') !== null");
        assertThat(tab.text("document.querySelector('.v-goal h1').textContent")).isEqualTo("Learn 3 new words");
        tab.eval("document.getElementById('startLearning').click()");
        tab.waitFor("document.querySelector('.v-meet[data-card=\"0\"]') !== null");
        assertThat(tab.text("document.querySelector('.v-meet').textContent")).contains("predator", "meaning of predator", "az predator");
        shot(tab, "meet");

        // The navbar asks before leaving a running session.
        tab.eval("window.confirm = () => false");
        tab.eval("document.querySelector('a[data-nav-link][href=\"/reading\"]').click()");
        assertThat(tab.text("location.pathname")).isEqualTo("/vocabulary/learn");

        for (int i = 0; i < 3; i++) {
            tab.waitFor("document.querySelector('.v-meet[data-card=\"" + i + "\"]') !== null");
            tab.eval("document.getElementById('meetNext').click()");
        }
        // Practise: the meaning, then the match.
        answer(tab, "meaning", "(() => [...document.querySelectorAll('.v-opt')].find(b => b.textContent.endsWith('meaning of predator')).click())()");
        next(tab);
        tab.waitFor("document.querySelector('.v-q[data-type=\"match\"]') !== null");
        tab.eval("""
                (() => { for (const t of document.querySelectorAll('[data-term]')) { t.click();
                  [...document.querySelectorAll('[data-meaning]')].find(m => m.textContent.endsWith('meaning of ' + t.textContent)).click(); }
                  document.querySelector('.v-check').click(); })()""");
        tab.waitFor("document.querySelector('.v-fb__verdict') !== null");
        assertThat(tab.text("document.querySelector('.v-fb__verdict').textContent")).isEqualTo("✓ Correct");
        next(tab);

        // Master: the first task wrong, so it comes back at the end.
        tab.waitFor("document.querySelector('.v-q[data-type=\"gap\"] .v-answer') !== null");
        tab.eval("(() => { document.querySelector('.v-answer').value = 'nonsense'; document.querySelector('.v-typed .btn').click(); })()");
        tab.waitFor("document.querySelector('.v-fb__retry') !== null");
        shot(tab, "wrong");
        next(tab);
        tab.waitFor("document.querySelector('.v-q[data-type=\"type\"]') !== null");
        typeAnswer(tab, "erosion");
        next(tab);
        tab.waitFor("document.querySelector('.v-q[data-type=\"listen\"]') !== null");
        typeAnswer(tab, "abundant");
        assertThat(tab.text("document.getElementById('masteryCount').textContent")).isEqualTo("1 / 3");
        next(tab);
        tab.waitFor("document.querySelector('.v-q[data-type=\"gap\"] .v-answer:not([disabled])') !== null");
        typeAnswer(tab, "predator");
        next(tab);
        tab.waitFor("document.getElementById('sessionDone') !== null");
        assertThat(tab.text("document.getElementById('sessionDone').textContent")).contains("3 new words learned", "Retries: 1");
        shot(tab, "done");
        assertThat(jdbc.queryForObject("""
                select count(*) from vocab_saved_word s join app_user u on u.id = s.user_id
                where u.display_name = 'Kamal' and s.state = 'LEARNING' and s.learned_at is not null""", Integer.class)).isEqualTo(3);
    }

    @Test
    void setsAreWorkedThroughInRoundsUntilEveryWordIsMastered() throws Exception {
        bank("predator", "erosion", "decline");
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, registerLearner(tab, "Tural"), PASSWORD);
        for (String w : List.of("predator", "erosion", "decline")) {
            tab.eval("import('/js/vocabulary/api.js').then(m => m.save('" + w + "', null))");
        }
        String tural = "user_id = (select id from app_user where display_name = 'Tural')";
        for (int i = 0; i < 50 && jdbc.queryForObject("select count(*) from vocab_saved_word where " + tural, Integer.class) < 3; i++) {
            Thread.sleep(100);
        }
        jdbc.update("update vocab_saved_word set state = 'LEARNING', learned_at = created_at where " + tural);

        // My words: the Sets card, then the sets page
        tab.navigate(url("/vocabulary"));
        tab.waitFor("!document.getElementById('vSetsGo').hidden");
        assertThat(tab.text("document.getElementById('vSetsTitle').textContent")).isEqualTo("Set 1 of 1 · 0 / 3 mastered");
        assertThat(tab.eval("document.querySelectorAll('#vSetsDots i').length").asInt()).isEqualTo(3);
        assertThat(tab.text("document.getElementById('vSetsGo').textContent")).isEqualTo("Continue Set 1 →");
        assertThat(tab.text("document.querySelector('.v-word .v-strength').textContent")).isEqualTo("Set 1 · not yet");
        shot(tab, "sets-card");
        tab.eval("document.getElementById('vSetsGo').click()");
        tab.waitFor("location.pathname === '/vocabulary/sets' && document.getElementById('vsStart') !== null");
        assertThat(tab.text("document.querySelector('.v-set').textContent")).contains("Set 1", "3 / 25 words", "0 / 3 mastered");
        shot(tab, "sets");
        tab.eval("document.getElementById('vsStart').click()");

        // Round 1: the first word missed (it comes back, the retry doesn't count), the others right once
        tab.waitFor("location.pathname === '/vocabulary/round' && document.querySelector('.v-q') !== null");
        assertThat(tab.text("document.querySelector('.v-pill').textContent")).contains("Set 1 · Round 1");
        assertThat(tab.text("String(document.querySelector('.v-q .v-hint'))")).as("no hint").isEqualTo("null");
        String first = asked(tab);
        tab.eval("document.querySelector('[data-dont-know]').click()");
        tab.waitFor("document.querySelector('.v-fb__verdict') !== null");
        assertThat(tab.text("document.querySelector('.v-fb').textContent"))
                .contains("✕ Not quite", "Correct: " + first, "comes back at the end of the round", "○ Missed");
        shot(tab, "round");
        next(tab);
        for (int i = 0; i < 2; i++) {
            tab.waitFor("document.querySelector('.v-q .v-fb[hidden]') !== null");
            typeAnswer(tab, asked(tab).toUpperCase() + "s");
            assertThat(tab.text("document.querySelector('[data-step]').dataset.step")).isEqualTo("once");
            next(tab);
        }
        tab.waitFor("document.querySelector('.v-q .v-fb[hidden]') !== null");
        assertThat(asked(tab)).isEqualTo(first);
        typeAnswer(tab, first);
        assertThat(tab.text("document.querySelector('.v-fb').textContent")).contains("Only first tries count");
        next(tab);
        tab.waitFor("document.getElementById('sessionDone') !== null && document.getElementById('roundSet') !== null");
        assertThat(tab.text("document.getElementById('roundScore').textContent")).isEqualTo("2 / 3");
        assertThat(tab.text("document.getElementById('roundMissed').textContent")).isEqualTo(first);
        assertThat(tab.eval("document.querySelectorAll('#roundOnce span').length").asInt()).isEqualTo(2);
        assertThat(tab.text("document.getElementById('roundSet').textContent")).isEqualTo("Set 1: 0 / 3 mastered");
        shot(tab, "round-done");

        // Round 2: two words mastered, the missed one right once; round 3 masters the set
        tab.eval("[...document.querySelectorAll('#sessionDone a')].find(a => a.textContent === 'Next round →').click()");
        tab.waitFor("document.querySelector('.v-pill') !== null && document.querySelector('.v-pill').textContent.includes('Round 2')");
        for (int i = 0; i < 3; i++) {
            tab.waitFor("document.querySelector('.v-q .v-fb[hidden]') !== null");
            typeAnswer(tab, asked(tab));
            next(tab);
        }
        tab.waitFor("document.getElementById('sessionDone') !== null && document.getElementById('roundSet') !== null");
        assertThat(tab.text("document.getElementById('roundMastered').textContent")).doesNotContain(first);
        assertThat(tab.text("document.getElementById('roundOnce').textContent")).isEqualTo(first);
        tab.eval("[...document.querySelectorAll('#sessionDone a')].find(a => a.textContent === 'Next round →').click()");
        tab.waitFor("document.querySelector('.v-pill') !== null && document.querySelector('.v-pill').textContent.includes('Round 3')");
        typeAnswer(tab, asked(tab));
        assertThat(tab.text("document.querySelector('[data-step]').dataset.step")).isEqualTo("mastered");
        next(tab);
        tab.waitFor("document.getElementById('sessionDone') !== null && document.getElementById('roundSet') !== null");
        assertThat(tab.text("document.querySelector('#sessionDone h1').textContent")).isEqualTo("Set 1 mastered!");
        assertThat(tab.text("document.getElementById('sessionDone').textContent")).contains("You mastered every learned word");
        shot(tab, "set-mastered");
        assertThat(jdbc.queryForObject("select count(*) from vocab_saved_word where state = 'MASTERED' and " + tural,
                Integer.class)).isEqualTo(3);
    }

    @Test
    void guestsAreAskedToSignInAndEmptySetsSaySo() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/vocabulary"));
        tab.waitFor("!document.getElementById('vGuest').hidden");
        tab.navigate(url("/vocabulary/learn"));
        tab.waitFor("document.querySelector('.v-msgcard h1') !== null");
        assertThat(tab.text("document.querySelector('.v-msgcard h1').textContent")).isEqualTo("Sign in to learn your words");
        signIn(tab, registerLearner(tab, "Rauf"), PASSWORD);
        tab.eval("location.href = '/vocabulary/review'"); // an old link lands on the sets
        tab.waitFor("location.pathname === '/vocabulary/sets' && document.getElementById('vsPage') !== null"
                + " && !document.getElementById('vsPage').hidden");
        assertThat(tab.eval("document.getElementById('vsEmpty').hidden").asBoolean()).isFalse();
        tab.navigate(url("/vocabulary/round"));
        tab.waitFor("document.querySelector('.v-msgcard h1') !== null");
        assertThat(tab.text("document.querySelector('.v-msgcard').textContent"))
                .contains("Nothing to ask right now", "Learn some words first — they join Set 1.");
    }

    /** The English word a round task asks for (the test cards' Azerbaijani is "az <word>"). */
    private static String asked(HeadlessChrome.Tab tab) throws Exception {
        return tab.text("document.querySelector('.v-az--big').textContent").replace("🇦🇿 az ", "");
    }

    // ------------------------------------------------------------- helpers

    private static StubOpenRouter start() {
        try {
            return new StubOpenRouter();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Puts READY cards in the bank (meaning "meaning of <word>", az "az <word>"), unless they're there. */
    private void bank(String... words) {
        for (String w : words) {
            if (jdbc.queryForObject("select count(*) from vocab_word_form where form = ?", Integer.class, w) > 0) {
                continue;
            }
            ObjectNode card = StubOpenRouter.card(w, List.of(w + "s"));
            card.putObject("meaning").put("en", "meaning of " + w).put("az", "az " + w);
            Long id = jdbc.queryForObject("""
                    insert into vocab_word (lemma, status, source, revision, content) values (?, 'READY', 'AI', 1, cast(? as jsonb))
                    returning id""", Long.class, w, card.toString());
            jdbc.update("insert into vocab_word_form (form, word_id) values (?, ?), (?, ?)", w, id, w + "s", id);
        }
    }

    private static void add(HeadlessChrome.Tab tab, String word) throws Exception {
        tab.eval("(() => { document.getElementById('vAddMsg').hidden = true; document.getElementById('vAddInput').value = '" + word
                + "'; document.getElementById('vAdd').requestSubmit(); })()");
        tab.waitFor("!document.getElementById('vAddMsg').hidden");
    }

    private static void answer(HeadlessChrome.Tab tab, String type, String action) throws Exception {
        tab.waitFor("document.querySelector('.v-q[data-type=\"" + type + "\"]') !== null");
        tab.eval(action);
        tab.waitFor("document.querySelector('.v-fb__verdict') !== null");
    }

    private static void typeAnswer(HeadlessChrome.Tab tab, String word) throws Exception {
        tab.eval("(() => { const i = [...document.querySelectorAll('.v-answer')].find(x => !x.disabled); i.value = '" + word
                + "'; document.querySelector('.v-typed .btn:not([hidden])').click(); })()");
        tab.waitFor("document.querySelector('.v-fb__verdict') !== null");
        assertThat(tab.text("document.querySelector('.v-fb__verdict').textContent")).isEqualTo("✓ Correct");
    }

    private static void next(HeadlessChrome.Tab tab) throws Exception {
        tab.eval("document.querySelector('[data-next]').click()");
    }

    private static void shot(HeadlessChrome.Tab tab, String name) throws Exception {
        tab.screenshot(Path.of("build/screenshots/vocabulary-" + name + ".png"));
    }
}
