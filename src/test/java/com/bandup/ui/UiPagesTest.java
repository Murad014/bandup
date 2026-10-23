package com.bandup.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.bandup.TestcontainersConfiguration;

/**
 * The page shells (frontend task 1): every route renders its template for
 * anyone, with navigation data only — no exercise data, no answers, no
 * server-side decision about who is an admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UiPagesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void everyPageRendersForAnonymousVisitors() throws Exception {
        for (String[] route : List.of(
                new String[] {"/", "index"},
                new String[] {"/reading", "catalog"},
                new String[] {"/reading/yellowstone-wolves", "reading"},
                new String[] {"/admin", "admin/index"},
                new String[] {"/admin/sessions", "admin/sessions"},
                new String[] {"/admin/sessions/new", "admin/session-edit"},
                new String[] {"/admin/sessions/yellowstone-wolves/edit", "admin/session-edit"},
                new String[] {"/login", "login"},
                new String[] {"/register", "register"})) {
            mvc.perform(get(route[0])).andExpect(status().isOk()).andExpect(view().name(route[1]));
        }
    }

    @Test
    void theNavbarHasItsItemsAndAHiddenAdminLink() throws Exception {
        String home = html("/");

        assertThat(home).contains("href=\"/reading\"", ">Reading<", ">Grammar<", "soon");
        assertThat(home).doesNotContain("data-nav-admin").doesNotContain("href=\"/admin\""); // no Admin link: admins type /admin
        // .app-nav__item sets display:flex, which would override [hidden] without this rule (seen in Chrome).
        assertThat(mvc.perform(get("/css/app.css")).andReturn().getResponse().getContentAsString())
                .contains("[hidden] { display: none !important; }");
        assertThat(html("/reading/yellowstone-wolves")).contains("aria-current=\"page\"");
    }

    @Test
    void theReadingPageCarriesOnlyTheSlugAndTheApiBase() throws Exception {
        String page = html("/reading/yellowstone-wolves");

        assertThat(page).contains("data-session-id=\"yellowstone-wolves\"", "data-api-base=\"/api/v1\"",
                "/js/reading.js", "/css/reading.css");
        assertThat(page).doesNotContain("riverbanks", "songbirds", "Wolves disappeared", "data-g=", "eliminated");
    }

    @Test
    void anUnknownSlugStillGetsTheShellTheApiAnswers404() throws Exception {
        assertThat(html("/reading/no-such-passage")).contains("data-session-id=\"no-such-passage\"");
    }

    @Test
    void aHostileSlugIsEscaped() throws Exception {
        String page = mvc.perform(get("/reading/{slug}", "x\" onmouseover=\"alert(1)"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(page).doesNotContain("onmouseover=\"alert(1)\"").contains("&quot;");
    }

    @Test
    void adminPagesHaveTheirSidebarAndNoData() throws Exception {
        String sessions = html("/admin/sessions");
        assertThat(sessions).contains("href=\"/admin\"", ">Dashboard<", ">Sessions<", "/js/admin/sessions-page.js");
        assertThat(sessions).doesNotContain("riverbanks", "yellowstone-wolves");

        String editor = html("/admin/sessions/yellowstone-wolves/edit");
        assertThat(editor).contains("data-session-id=\"yellowstone-wolves\"", "/js/admin/editor-page.js");
        assertThat(editor).doesNotContain("riverbanks", "FALSE", "eliminated");
        // A new session has no id: Thymeleaf leaves the empty attribute out, and editor.js reads that as "new".
        assertThat(html("/admin/sessions/new")).contains("<main id=\"editorRoot\">").doesNotContain("data-session-id");
    }

    private String html(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
