package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A whole grammar lesson in Chrome (grammar requirements 4, 5): the A2 seed
 * lesson, Goal → Learn → worked example → practice → mastery (a wrong item
 * comes back) → done, graded by the real API, with the navbar guard on while
 * the lesson runs.
 */
class GrammarLessonBrowserTest extends BrowserTestBase {

    private static final String A2 = "/grammar/lessons/a2-articles-1";

    @Test
    void aGuestDoesAWholeLessonAndAWrongItemComesBack() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url(A2));
        tab.waitFor("document.getElementById('startLearning') !== null");
        assertThat(tab.text("document.querySelector('.g-goal h1').textContent")).isEqualTo("Articles: a / an / the");
        assertThat(tab.text("document.title")).isEqualTo("Articles: a / an / the · Orbiko");
        assertThat(tab.text("document.querySelector('.g-pill.is-active').dataset.stage")).isEqualTo("goal");

        shot(tab, "goal");
        tab.eval("document.getElementById('startLearning').click()");
        tab.waitFor("document.querySelector('.g-teach[data-card=\"0\"]') !== null");
        shot(tab, "learn");
        learnAllCards(tab);

        // The navbar asks before leaving a running lesson.
        tab.eval("window.confirm = () => false");
        tab.eval("document.querySelector('a[data-nav-link][href=\"/reading\"]').click()");
        assertThat(tab.text("location.pathname")).isEqualTo(A2);

        // Worked example, then guided practice.
        tab.waitFor("document.getElementById('workedNext') !== null");
        assertThat(tab.text("document.querySelector('.g-worked__answer').textContent")).contains("an hour");
        tab.eval("document.getElementById('workedNext').click()");
        assertThat(choose(tab, "p1", 0)).startsWith("✕ Not quite");
        assertThat(tab.text("document.querySelector('.g-fb__why').textContent")).contains("“a” —", "vowel sound");
        assertThat(tab.text("document.querySelector('.g-fb__answer').textContent")).isEqualTo("Correct: an");
        shot(tab, "wrong");
        next(tab);
        assertThat(choose(tab, "p2", 1)).startsWith("✓ Correct");
        next(tab);
        assertThat(build(tab, "p3", List.of("She", "is", "an", "engineer"))).startsWith("✓ Correct");
        next(tab);

        // Mastery: c2 wrong first, so it comes back at the end.
        assertThat(choose(tab, "c1", 1)).startsWith("✓");
        next(tab);
        assertThat(choose(tab, "c2", 0)).startsWith("✕");
        assertThat(tab.text("document.querySelector('.g-fb__retry').textContent")).contains("comes back");
        next(tab);
        assertThat(type(tab, "c3", "An")).startsWith("✓");
        next(tab);
        assertThat(type(tab, "c4", "a")).startsWith("✓");
        next(tab);
        assertThat(choose(tab, "c5", 2)).startsWith("✓");
        next(tab);
        tab.waitFor("document.querySelector('.g-q[data-item=\"c6\"]') !== null");
        tab.eval("document.querySelector('.g-bank .g-tile').click()");
        shot(tab, "build");
        assertThat(answer(tab, "c6", "(() => { const tray = document.querySelector('.g-tray .g-tile'); tray.click();"
                + " for (const w of ['Close','the','door','please']) [...document.querySelectorAll('.g-bank .g-tile')]"
                + ".find(t => !t.disabled && t.textContent === w).click(); document.querySelector('.g-check').click(); })()"))
                .startsWith("✓");
        next(tab);
        assertThat(choose(tab, "c7", 0)).startsWith("✓");
        assertThat(tab.text("document.getElementById('masteryCount').textContent")).isEqualTo("5 / 7");
        next(tab);
        assertThat(choose(tab, "c2", 3)).startsWith("✓");
        next(tab);

        tab.waitFor("document.getElementById('lessonDone') !== null");
        shot(tab, "done");
        assertThat(tab.text("document.getElementById('lessonDone').textContent"))
                .contains("Lesson complete — 100%", "Retries: 1", "7/7 items", "sign in to unlock the next lessons");
        // The guard is off again.
        tab.eval("window.confirm = () => { throw new Error('asked'); }");
        tab.eval("document.querySelector('a[data-nav-link][href=\"/reading\"]').click()");
        tab.waitFor("location.pathname === '/reading'");
    }

    @Test
    void aLearnerFinishesALessonAndTheCourseShowsItDoneWithTheReviewComing() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, registerLearner(tab, "Nigar"), PASSWORD);
        tab.navigate(url(A2));
        tab.waitFor("document.getElementById('startLearning') !== null");
        tab.eval("document.getElementById('startLearning').click()");
        learnAllCards(tab);
        tab.waitFor("document.getElementById('workedNext') !== null");
        tab.eval("document.getElementById('workedNext').click()");
        choose(tab, "p1", 1);
        next(tab);
        choose(tab, "p2", 1);
        next(tab);
        build(tab, "p3", List.of("She", "is", "an", "engineer"));
        next(tab);
        choose(tab, "c1", 1);
        next(tab);
        choose(tab, "c2", 3);
        next(tab);
        type(tab, "c3", "an");
        next(tab);
        type(tab, "c4", "a");
        next(tab);
        choose(tab, "c5", 2);
        next(tab);
        build(tab, "c6", List.of("Close", "the", "door", "please"));
        next(tab);
        choose(tab, "c7", 0);
        next(tab);
        tab.waitFor("document.getElementById('lessonDone') !== null");
        assertThat(tab.text("document.getElementById('lessonDone').textContent"))
                .contains("Retries: 0", "come back in your daily review");

        tab.navigate(url("/grammar?level=A2"));
        tab.waitFor("document.querySelector('.g-day[data-slug=\"a2-articles-1\"]') !== null");
        assertThat(tab.text("document.querySelector('.g-day[data-slug=\"a2-articles-1\"]').dataset.state")).isEqualTo("DONE");
        assertThat(tab.text("document.getElementById('reviewSub').textContent")).contains("next review tomorrow");
        assertThat(tab.text("document.getElementById('courseSub').textContent")).contains("done");
    }

    @Test
    void aLearnCardAndAnItemFitAPhoneScreen() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        tab.call("Emulation.setDeviceMetricsOverride", tools.jackson.databind.json.JsonMapper.builder().build()
                .createObjectNode().put("width", 375).put("height", 740).put("deviceScaleFactor", 2).put("mobile", true));
        tab.navigate(url(A2));
        tab.waitFor("document.getElementById('startLearning') !== null");
        tab.eval("document.getElementById('startLearning').click()");
        tab.waitFor("document.querySelector('.g-teach[data-card=\"0\"]') !== null");
        for (int i = 0; i < 4; i++) {
            tab.eval("document.getElementById('learnNext').click()");
        }
        tab.waitFor("document.querySelector('.g-teach[data-card=\"4\"]') !== null");
        assertThat(tab.eval("document.documentElement.scrollWidth <= window.innerWidth + 1").asBoolean()).isTrue();
        shot(tab, "learn-phone");
        tab.eval("document.getElementById('learnNext').click()");
        tab.waitFor("document.getElementById('workedNext') !== null");
        tab.eval("document.getElementById('workedNext').click()");
        choose(tab, "p1", 0);
        assertThat(tab.eval("document.documentElement.scrollWidth <= window.innerWidth + 1").asBoolean()).isTrue();
        shot(tab, "wrong-phone");
    }

    @Test
    void anUnknownLessonSaysSo() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/grammar/lessons/no-such-lesson"));
        tab.waitFor("document.querySelector('.g-msg h1') !== null");
        assertThat(tab.text("document.querySelector('.g-msg h1').textContent")).isEqualTo("Lesson not found");
    }

    // ------------------------------------------------------------- helpers

    private static void shot(HeadlessChrome.Tab tab, String name) throws Exception {
        tab.screenshot(java.nio.file.Path.of("build/screenshots/grammar-lesson-" + name + ".png"));
    }

    private static void learnAllCards(HeadlessChrome.Tab tab) throws Exception {
        tab.waitFor("document.querySelector('.g-teach[data-card=\"0\"]') !== null");
        assertThat(tab.text("document.querySelector('.g-pill.is-active').dataset.stage")).isEqualTo("learn");
        for (int i = 0; i < 5; i++) {
            tab.waitFor("document.querySelector('.g-teach[data-card=\"" + i + "\"]') !== null");
            tab.eval("document.getElementById('learnNext').click()");
        }
    }

    /** Waits for the item, answers it and returns the verdict line. */
    private static String answer(HeadlessChrome.Tab tab, String item, String action) throws Exception {
        tab.waitFor("document.querySelector('.g-q[data-item=\"" + item + "\"] .g-fb[hidden]') !== null");
        tab.eval(action);
        tab.waitFor("document.querySelector('.g-q[data-item=\"" + item + "\"] .g-fb:not([hidden])') !== null");
        return tab.text("document.querySelector('.g-fb__verdict').textContent");
    }

    private static String choose(HeadlessChrome.Tab tab, String item, int option) throws Exception {
        return answer(tab, item, "document.querySelector('.g-choice[data-index=\"" + option + "\"]').click()");
    }

    private static String type(HeadlessChrome.Tab tab, String item, String text) throws Exception {
        return answer(tab, item, "(() => { document.querySelector('.g-input').value = '" + text
                + "'; document.querySelector('.g-check').click(); })()");
    }

    private static String build(HeadlessChrome.Tab tab, String item, List<String> words) throws Exception {
        StringBuilder js = new StringBuilder("(() => {");
        for (String word : words) {
            js.append("[...document.querySelectorAll('.g-bank .g-tile')].find(t => !t.disabled && t.textContent === '")
                    .append(word).append("').click();");
        }
        js.append("document.querySelector('.g-check').click(); })()");
        return answer(tab, item, js.toString());
    }

    private static void next(HeadlessChrome.Tab tab) throws Exception {
        tab.eval("document.querySelector('.g-next').click()");
    }
}
