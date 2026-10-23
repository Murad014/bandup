package com.bandup.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A basic accessibility check of every page (frontend final task): each
 * visible form control has an accessible name, every button says what it
 * does, images have alt text, and the page works at phone width without
 * sideways scrolling.
 */
class AccessibilityBrowserTest extends BrowserTestBase {

    /** Visible controls without a name: no label, no aria-label, no title. */
    private static final String UNNAMED = """
            [...document.querySelectorAll('input:not([type=hidden]), select, textarea, button, [contenteditable=true]')]
              .filter(el => el.offsetParent !== null)
              .filter(el => {
                const named = (el.labels && [...el.labels].some(l => l.textContent.trim()))
                  || (el.getAttribute('aria-label') || '').trim()
                  || (el.getAttribute('title') || '').trim()
                  || (el.tagName === 'BUTTON' && el.textContent.trim());
                return !named;
              })
              .map(el => el.outerHTML.slice(0, 120))""";

    @Test
    void publicPagesHaveNamedControls() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        for (String page : List.of("/", "/login", "/register", "/reading/yellowstone-wolves")) {
            tab.navigate(url(page));
            if (page.startsWith("/reading")) {
                tab.waitFor("document.querySelectorAll('#questions .q').length === 6");
            }
            assertThat(tab.eval(UNNAMED).toString()).as(page).isEqualTo("[]");
            assertThat(tab.eval("[...document.images].filter(i => !i.hasAttribute('alt')).length").asInt()).as(page).isZero();
        }
    }

    @Test
    void adminPagesHaveNamedControls() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        signIn(tab, ADMIN_EMAIL, ADMIN_PASSWORD);
        tab.navigate(url("/admin/sessions"));
        tab.waitFor("document.querySelector('tr[data-id]') !== null");
        assertThat(tab.eval(UNNAMED).toString()).as("list").isEqualTo("[]");

        tab.navigate(url("/admin/sessions/yellowstone-wolves/edit"));
        tab.waitFor("document.querySelectorAll('.gloss-row').length === 25");
        assertThat(tab.eval(UNNAMED).toString()).as("editor").isEqualTo("[]");
    }

    @Test
    void theReadingPageFitsAPhoneScreen() throws Exception {
        HeadlessChrome.Tab tab = chrome.newTab(url("/"));
        tab.call("Emulation.setDeviceMetricsOverride", tools.jackson.databind.json.JsonMapper.builder().build()
                .createObjectNode().put("width", 375).put("height", 740).put("deviceScaleFactor", 2).put("mobile", true));
        tab.navigate(url("/reading/yellowstone-wolves"));
        tab.waitFor("document.querySelectorAll('#questions .q').length === 6");

        assertThat(tab.eval("document.documentElement.scrollWidth <= window.innerWidth + 1").asBoolean()).isTrue();
        tab.screenshot(java.nio.file.Path.of("build/screenshots/reading-phone.png"));
    }
}
