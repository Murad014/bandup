package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** "My progress": the navbar link, the page, and reviewing an attempt on the reading page. */
class ProgressBrowserTest extends BrowserTestBase {

    @Test
    void aLearnerPractisesThenSeesAndReviewsTheAttempt() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        tab.waitFor("!document.querySelector('[data-nav-signin]').hidden");
        assertThat(tab.eval("document.querySelector('[data-nav-progress]').hidden").asBoolean()).isTrue(); // signed out

        signIn(tab, registerLearner(tab, "Progress"), PASSWORD);
        tab.navigate(url("/reading/yellowstone-wolves"));
        tab.waitFor("document.querySelectorAll('#questions .q').length === 6");
        tab.eval("document.getElementById('q1o0').click()"); // TRUE (wrong)
        tab.eval("(() => { const i = document.getElementById('in4'); i.value = 'riverbanks'; i.dispatchEvent(new Event('input', {bubbles: true})); })()");
        tab.eval("document.getElementById('submit').click()");
        tab.waitFor("!document.getElementById('resultBox').hidden");

        // The navbar link (signed in) opens the page with the summary and the attempt.
        tab.waitFor("!document.querySelector('[data-nav-progress]').hidden");
        tab.eval("document.querySelector('[data-nav-progress] a').click()");
        tab.waitFor("location.pathname === '/progress' && document.querySelector('.attempt') !== null");
        assertThat(tab.text("document.getElementById('statAttempts').textContent")).isEqualTo("1");
        assertThat(tab.text("document.getElementById('statPassages').textContent")).isEqualTo("1");
        assertThat(tab.text("document.getElementById('statAverage').textContent")).isEqualTo("17%");
        assertThat(tab.text("document.querySelector('.attempt h3').textContent")).isEqualTo("The Return of the Wolves");
        assertThat(tab.text("document.querySelector('.attempt__score').textContent")).isEqualTo("1/617%");
        assertThat(tab.text("document.querySelector('.attempt__cover img').getAttribute('src')")).isEqualTo("/img/covers/nature.svg");
        assertThat(tab.text("document.querySelector('[data-nav-progress] a').className")).contains("is-active");

        // Review: the stored answers are back, graded, without a running timer.
        tab.eval("[...document.querySelectorAll('.attempt a')].find(a => a.textContent === 'Review').click()");
        tab.waitFor("location.pathname === '/reading/yellowstone-wolves' && location.search.startsWith('?review=')"
                + " && document.querySelector('.review-note') !== null");
        assertThat(tab.eval("document.getElementById('q1o0').checked").asBoolean()).isTrue();
        assertThat(tab.eval("document.getElementById('q1').classList.contains('wrong')").asBoolean()).isTrue();
        assertThat(tab.text("document.getElementById('in4').value")).isEqualTo("riverbanks");
        assertThat(tab.eval("document.getElementById('q4').classList.contains('correct')").asBoolean()).isTrue();
        assertThat(tab.text("document.querySelector('#q1 .fb').textContent")).contains("You answered: TRUE", "Answer: FALSE");
        assertThat(tab.text("document.getElementById('resultBox').textContent")).contains("Reviewing your attempt", "1/6");
        assertThat(tab.text("document.getElementById('timer').textContent")).isEqualTo("--:--");

        // "Start again" practises anew.
        tab.eval("document.getElementById('reset').click()");
        tab.waitFor("location.search === '' && !document.getElementById('q1o0').checked && !document.getElementById('q1o0').disabled");
        assertThat(tab.text("document.getElementById('timer').textContent")).isNotEqualTo("--:--");
    }

    @Test
    void signedOutVisitorsAreSentToSignInAndBack() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        tab.open(url("/progress"));
        tab.waitFor("location.pathname === '/login' && location.search === '?next=%2Fprogress'");
    }

    @Test
    void aNewLearnerIsInvitedToStart() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, registerLearner(tab, "Newcomer"), PASSWORD);
        tab.navigate(url("/progress"));
        tab.waitFor("document.getElementById('progressStatus').textContent.includes(\"You haven't practised yet\")");
        assertThat(tab.text("document.querySelector('#progressStatus a').getAttribute('href')")).isEqualTo("/reading");
        assertThat(tab.text("document.getElementById('statAttempts').textContent")).isEqualTo("0");
    }
}
