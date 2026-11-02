package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The daily grammar review in Chrome (grammar requirement 6): a learner's
 * finished A2 lesson becomes 7 cards; once due they are reviewed with the
 * topic shown, a wrong card comes back until right, and every answer says
 * when the card is next due.
 */
class GrammarReviewBrowserTest extends BrowserTestBase {

    /** The right answer of each A2 seed check item, as JS that answers the item on screen. */
    private static final String RIGHT = """
            ({ c1: () => pick(1), c2: () => pick(3), c3: () => typeIn('an'), c4: () => typeIn('a'), c5: () => pick(2),
               c6: () => build(['Close','the','door','please']), c7: () => pick(0) })""";

    private static final String HELPERS = """
            const pick = i => document.querySelector('.g-choice[data-index="' + i + '"]').click();
            const typeIn = v => { document.querySelector('.g-input').value = v; document.querySelector('.g-check').click(); };
            const build = ws => { for (const w of ws) [...document.querySelectorAll('.g-bank .g-tile')]
                .find(t => !t.disabled && t.textContent === w).click(); document.querySelector('.g-check').click(); };
            """;

    /** A wrong answer per item; build uses the tiles as the server shuffled them (never the answer order). */
    private static final String WRONG = """
            ({ c1: () => pick(0), c2: () => pick(0), c3: () => typeIn('zzz'), c4: () => typeIn('zzz'), c5: () => pick(0),
               c6: () => build([...document.querySelectorAll('.g-bank .g-tile')].map(t => t.textContent)), c7: () => pick(1) })""";

    @Test
    void aLearnerReviewsDueCardsAndAWrongCardComesBack() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        String email = registerLearner(tab, "Leyla");
        signIn(tab, email, PASSWORD);
        finishA2Lesson(tab);
        // The deck is due tomorrow; move it back so it is due today.
        assertThat(jdbc.update("update grammar_review_card set due_on = due_on - 7 where user_id ="
                + " (select id from app_user where email = ?)", email)).isEqualTo(7);

        tab.navigate(url("/grammar?level=A2"));
        tab.waitFor("!document.getElementById('reviewGo').hidden");
        assertThat(tab.text("document.getElementById('reviewSub').textContent")).startsWith("7 items due");
        tab.eval("document.getElementById('reviewGo').click()");
        tab.waitFor("location.pathname === '/grammar/review'");

        tab.waitFor("document.querySelector('.g-q') !== null");
        assertThat(tab.text("document.querySelector('.g-q__kick').textContent")).contains("Articles");
        assertThat(tab.text("document.getElementById('reviewCount').textContent")).isEqualTo("0 / 7");
        String first = tab.text("document.querySelector('.g-q').dataset.item");
        answer(tab, first, WRONG + "['" + first + "']()");
        assertThat(verdict(tab)).startsWith("✕ Not quite");
        assertThat(tab.text("document.querySelector('.g-fb').textContent"))
                .contains("comes back until you get it right", "⏱ Next review: tomorrow");
        shot(tab, "wrong");
        next(tab);

        for (int i = 0; i < 6; i++) {
            String item = tab.text("document.querySelector('.g-q').dataset.item");
            assertThat(item).isNotEqualTo(first);
            answer(tab, item, RIGHT + "['" + item + "']()");
            assertThat(verdict(tab)).startsWith("✓ Correct");
            assertThat(tab.text("document.querySelector('.g-fb__next').textContent")).isEqualTo("⏱ Next review: in 3 days");
            next(tab);
        }
        // The wrong card is last; right now, but its schedule stays from the first answer today.
        assertThat(tab.text("document.querySelector('.g-q').dataset.item")).isEqualTo(first);
        assertThat(tab.text("document.getElementById('reviewCount').textContent")).isEqualTo("6 / 7");
        answer(tab, first, RIGHT + "['" + first + "']()");
        assertThat(verdict(tab)).startsWith("✓ Correct");
        assertThat(tab.text("document.querySelector('.g-fb__next').textContent")).isEqualTo("⏱ Next review: tomorrow");
        next(tab);

        tab.waitFor("document.getElementById('reviewDone') !== null");
        shot(tab, "done");
        assertThat(tab.text("document.getElementById('reviewDone').textContent"))
                .contains("Review done", "Reviewed: 7 cards", "Retries: 1", "Every due card is reviewed");
        assertThat(jdbc.queryForObject("select count(*) from grammar_review_card where box = 2 and user_id ="
                + " (select id from app_user where email = ?)", Integer.class, email)).isEqualTo(6);

        tab.navigate(url("/grammar?level=A2"));
        tab.waitFor("document.getElementById('reviewSub').textContent !== ''");
        assertThat(tab.text("document.getElementById('reviewSub').textContent")).contains("Nothing due today · next review tomorrow");
    }

    @Test
    void aLearnerWithNothingDueIsToldSo() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, registerLearner(tab, "Kamal"), PASSWORD);
        tab.navigate(url("/grammar/review"));
        tab.waitFor("document.getElementById('reviewEmpty') !== null");
        assertThat(tab.text("document.querySelector('#reviewEmpty h1').textContent")).isEqualTo("Nothing to review");
    }

    @Test
    void aGuestIsAskedToSignIn() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/grammar/review"));
        tab.waitFor("document.querySelector('.g-msg h1') !== null");
        assertThat(tab.text("document.querySelector('.g-msg h1').textContent")).isEqualTo("Sign in to review");
        assertThat(tab.text("document.querySelector('.g-msg a.btn--primary').getAttribute('href')"))
                .isEqualTo("/login?next=%2Fgrammar%2Freview");
    }

    // ------------------------------------------------------------- helpers

    /** Finishes the A2 seed lesson through the learner API, every check item right first time. */
    private static void finishA2Lesson(HeadlessChrome.Tab tab) throws Exception {
        String finished = tab.text("""
                import('/js/grammar/api.js').then(async api => {
                  const run = await api.startRun('a2-articles-1');
                  const right = { c1: 1, c2: 3, c3: 'an', c4: 'a', c5: 2, c6: ['Close','the','door','please'], c7: 0 };
                  let last;
                  for (const item of run.check) last = await api.answer(run.runId, item.id, right[item.id]);
                  return String(last.finished);
                })""");
        assertThat(finished).isEqualTo("true");
    }

    private static void answer(HeadlessChrome.Tab tab, String item, String action) throws Exception {
        tab.waitFor("document.querySelector('.g-q[data-item=\"" + item + "\"] .g-fb[hidden]') !== null");
        tab.eval("(() => {" + HELPERS + action + "; })()");
        tab.waitFor("document.querySelector('.g-q[data-item=\"" + item + "\"] .g-fb:not([hidden])') !== null");
    }

    private static String verdict(HeadlessChrome.Tab tab) throws Exception {
        return tab.text("document.querySelector('.g-fb__verdict').textContent");
    }

    private static void next(HeadlessChrome.Tab tab) throws Exception {
        tab.eval("document.querySelector('.g-next').click()");
    }

    private static void shot(HeadlessChrome.Tab tab, String name) throws Exception {
        tab.screenshot(java.nio.file.Path.of("build/screenshots/grammar-review-" + name + ".png"));
    }
}
