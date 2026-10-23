package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The reading page on the learner API in Chrome (frontend reading tasks 1–5). */
class ReadingBrowserTest extends BrowserTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void wolvesLoadsFromTheApiWithAzerbaijaniHelp() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        tab.eval("localStorage.setItem('bandup.helpLanguage', 'az')");
        tab.navigate(url("/reading/yellowstone-wolves"));
        tab.waitFor("document.querySelectorAll('#questions .q').length === 6");

        assertThat(tab.text("document.getElementById('passageTitle').textContent")).isEqualTo("The Return of the Wolves");
        assertThat(tab.text("document.getElementById('stageLabel').textContent")).isEqualTo("Mərhələ 1 · Passage 4");
        assertThat(tab.text("document.getElementById('timer').textContent")).matches("[45]:\\d\\d");
        assertThat(tab.text("document.getElementById('vocabTitle').textContent")).isEqualTo("Word list (Azərbaycanca)");
        assertThat(tab.text("[...document.querySelectorAll('.qgroup h3')].map(h => h.textContent).join('|')"))
                .isEqualTo("Questions 1–3|Questions 4–6");
        // The instruction already says ONE WORD ONLY, so no repeated hint.
        assertThat(tab.eval("document.querySelectorAll('.qgroup > .limit').length").asInt()).isZero();

        tab.eval("document.querySelector('#passage .w[data-g=g1]').click()");
        tab.waitFor("!document.getElementById('pop').hidden");
        assertThat(tab.text("document.getElementById('pop').textContent")).isEqualTo("eliminatedməhv edilmək");
        // No answers anywhere in the page before submitting.
        assertThat(tab.text("document.getElementById('questions').textContent")).doesNotContain("riverbanks", "hunting");
    }

    @Test
    void changingTheHelpLanguageKeepsTheAnswers() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        tab.eval("localStorage.setItem('bandup.helpLanguage', 'az')");
        tab.navigate(url("/reading/yellowstone-wolves"));
        tab.waitFor("document.querySelectorAll('#questions .q').length === 6");
        tab.eval(type("#in4", "riverbanks"));
        tab.eval("document.getElementById('q1o1').click()");

        tab.eval("(() => { const s = document.getElementById('helpLang'); s.value = 'en'; s.dispatchEvent(new Event('change')); })()");

        tab.waitFor("document.getElementById('vocabTitle').textContent === 'Word list (English)'");
        tab.eval("document.querySelector('#passage .w[data-g=g1]').click()");
        assertThat(tab.text("document.getElementById('pop').textContent")).isEqualTo("eliminatedremoved completely");
        assertThat(tab.text("document.getElementById('in4').value")).isEqualTo("riverbanks");
        assertThat(tab.eval("document.getElementById('q1o1').checked").asBoolean()).isTrue();
        assertThat(tab.text("localStorage.getItem('bandup.helpLanguage')")).isEqualTo("en");
    }

    @Test
    void aPerfectWolvesAttemptShowsResultsExplanationsAndEvidence() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/reading/yellowstone-wolves"));
        tab.waitFor("document.querySelectorAll('#questions .q').length === 6");
        tab.eval("document.getElementById('q1o1').click()"); // FALSE
        tab.eval("document.getElementById('q2o0').click()"); // TRUE
        tab.eval("document.getElementById('q3o2').click()"); // NOT GIVEN
        tab.eval(type("#in4", "Riverbanks"));
        tab.eval(type("#in5", "songbirds"));
        tab.eval(type("#in6", "wolves"));

        tab.eval("document.getElementById('submit').click()");

        tab.waitFor("!document.getElementById('resultBox').hidden");
        assertThat(tab.text("document.querySelector('#resultBox .big').textContent")).isEqualTo("6/6");
        assertThat(tab.eval("document.querySelectorAll('.q.correct').length").asInt()).isEqualTo(6);
        assertThat(tab.text("document.querySelector('#q1 .why').innerHTML")).contains("<b>hunting</b>");
        assertThat(tab.eval("document.querySelectorAll('#questions input:not(:disabled)').length").asInt()).isZero();

        tab.eval("document.querySelector('#q1 [data-ev]').click()");
        assertThat(tab.eval("document.getElementById('e1').classList.contains('on')").asBoolean()).isTrue();

        tab.eval("document.getElementById('reset').click()");
        tab.waitFor("document.getElementById('resultBox').hidden && document.querySelectorAll('.q.correct').length === 0");
        assertThat(tab.text("document.getElementById('in4').value")).isEmpty();
    }

    @Test
    void everyQuestionFamilyRendersAndIsGradedByTheServer() throws Exception {
        String slug = "families-" + UUID.randomUUID().toString().substring(0, 8);
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);
        tab.eval(AUTH + ".then(m => m.apiFetch('/api/v1/admin/passages', {method: 'POST', json: " + everyFamily(slug) + "}))"
                + ".then(() => import('/js/auth.js')).then(m => m.apiFetch('/api/v1/admin/passages/" + slug + "/publish', {method: 'POST'}))");

        tab.navigate(url("/reading/" + slug));
        tab.waitFor("document.querySelectorAll('#questions .q').length === 7");
        assertThat(tab.text("[...document.querySelectorAll('.qgroup h3')].map(h => h.textContent).join('|')"))
                .isEqualTo("Question 1|Question 2|Questions 3–4|Questions 5–6|Question 7|Question 8");
        assertThat(tab.text("document.querySelector('#q3 .num').textContent")).isEqualTo("3–4");
        assertThat(tab.text("document.querySelector('.qgroup[data-group=\"4\"] .limit').textContent"))
                .isEqualTo("NO MORE THAN TWO WORDS AND/OR A NUMBER");
        assertThat(tab.eval("document.querySelectorAll('.bank dt').length").asInt()).isEqualTo(3);

        tab.eval("document.getElementById('q1o0').click()");               // TFNG TRUE (right)
        tab.eval("document.querySelector('#q2 input[value=B]').click()");    // MCQ B (right)
        tab.eval("document.querySelector('#q3 input[value=A]').click()");    // Choose TWO: A right...
        tab.eval("document.querySelector('#q3 input[value=C]').click()");    // ...C wrong
        assertThat(tab.eval("document.querySelector('#q3 input[value=D]').disabled").asBoolean()).isTrue(); // limit reached
        tab.eval("(() => { const s = document.getElementById('sel5'); s.value = 'ii'; s.dispatchEvent(new Event('change', {bubbles: true})); })()");
        assertThat(tab.text("[...document.getElementById('sel6').options].find(o => o.value === 'ii').textContent")).isEqualTo("ii (used)");
        tab.eval("(() => { const s = document.getElementById('sel6'); s.value = 'iii'; s.dispatchEvent(new Event('change', {bubbles: true})); })()");
        tab.eval(type("#in7", "17 grey wolves"));                           // gap with AND/OR A NUMBER (right)
        tab.eval(type("#in8", "Yellowstone"));                              // short answer (right)

        tab.eval("document.getElementById('submit').click()");
        tab.waitFor("!document.getElementById('resultBox').hidden");

        assertThat(tab.text("document.querySelector('#resultBox .big').textContent")).isEqualTo("6/8");
        assertThat(tab.text("document.querySelector('#q3 .tag').textContent"))
                .isEqualTo("◐ Partly right (1/2) · You answered: A, C · Answer: A, D");
        assertThat(tab.eval("document.getElementById('q3').classList.contains('partial')").asBoolean()).isTrue();
        assertThat(tab.text("document.querySelector('#q6 .tag').textContent")).startsWith("✗ · You answered: iii · Answer: i");
        assertThat(tab.text("document.querySelector('#q7 .also').textContent")).isEqualTo(" Also accepted: 17 wolves");
        tab.screenshot(java.nio.file.Path.of("build/screenshots/reading-every-family-results.png"));
    }

    @Test
    void aSignedInLearnersAttemptIsSavedForThem() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        String email = registerLearner(tab, "Reader");
        signIn(tab, email, PASSWORD);
        tab.navigate(url("/reading/yellowstone-wolves"));
        tab.waitFor("document.querySelectorAll('#questions .q').length === 6");

        tab.eval("document.getElementById('submit').click()");
        tab.waitFor("!document.getElementById('resultBox').hidden");

        assertThat(jdbc.queryForObject("""
                select count(*) from attempt a join app_user u on u.id = a.user_id where u.email = ?""",
                Integer.class, email)).isEqualTo(1);
        assertThat(tab.text("document.querySelector('#resultBox .big').textContent")).isEqualTo("0/6");
    }

    @Test
    void anUnknownPassageSaysSoAndTheFixtureIsGone() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/reading/no-such-passage"));
        tab.waitFor("document.getElementById('passage').textContent.includes(\"isn't available\")");
        assertThat(tab.eval("document.getElementById('submit').disabled").asBoolean()).isTrue();

        assertThat(tab.eval("fetch('/js/reading/fixtures.js').then(r => r.status)").asInt()).isEqualTo(404);
    }

    /** TFNG, MCQ, "Choose TWO", headings (bank, no reuse), gap with a number, short answer. */
    private static String everyFamily(String slug) {
        return """
                {id: '%s', title: 'Every family', format: 'MINI', durationSeconds: 600,
                 passageHtml: '<p><span class="ev" id="e1">In 1995, <span class="w" data-g="g1">grey</span> wolves came back to Yellowstone.</span></p>',
                 glosses: {g1: {meanings: {en: {text: 'a colour'}}}},
                 questionGroups: [
                  {type: 'TFNG', instruction: 'True or false?', questions: [{text: 'Wolves came back.', evidenceId: 'e1', answer: ['TRUE'],
                     explanations: {en: {text: 'The text says they <b>came back</b>.'}}}]},
                  {type: 'MCQ', instruction: 'Choose one.', questions: [{text: 'When?', answer: ['B'],
                     options: [{key: 'A', label: '1990'}, {key: 'B', label: '1995'}, {key: 'C', label: '2000'}]}]},
                  {type: 'MCMA', instruction: 'Choose TWO.', chooseCount: 2, questions: [{text: 'Which TWO?', answer: ['A', 'D'],
                     options: [{key: 'A', label: 'Wolves'}, {key: 'B', label: 'Bears'}, {key: 'C', label: 'Elk'}, {key: 'D', label: 'Grey'}]}]},
                  {type: 'MATCH_HEADINGS', instruction: 'Choose the heading.', optionsReusable: false,
                   options: [{key: 'i', label: 'Return'}, {key: 'ii', label: 'Change'}, {key: 'iii', label: 'Doubt'}],
                   questions: [{text: 'Paragraph A', answer: ['ii']}, {text: 'Paragraph B', answer: ['i']}]},
                  {type: 'FILL', instruction: 'Complete.', maxWords: 2, numbersAllowed: true,
                   questions: [{pre: 'In 1995,', post: 'came back.', answer: ['17 grey wolves', '17 wolves']}]},
                  {type: 'SHORT', instruction: 'Answer briefly.', questions: [{text: 'Which park?', answer: ['Yellowstone']}]}
                 ]}""".formatted(slug).replace("\n", " ");
    }
}
