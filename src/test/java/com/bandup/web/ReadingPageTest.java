package com.bandup.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.bandup.TestcontainersConfiguration;
import org.springframework.context.annotation.Import;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies GET /reading/{id} serves the Reading shell inside the app layout:
 * navbar with Reading active, the session sub-bar + two panels, the embedded
 * session id, the page stylesheet, and the reading.js module script.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReadingPageTest {

    @Autowired
    MockMvc mvc;

    @Test
    void servesReadingShell() throws Exception {
        mvc.perform(get("/reading/yellowstone-wolves"))
                .andExpect(status().isOk())
                // Shared shell / navbar, with Reading marked active
                .andExpect(content().string(containsString("app-nav__brand")))
                .andExpect(content().string(containsString("is-active")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                // Session config embedded on the root element
                .andExpect(content().string(containsString("id=\"reading-root\"")))
                .andExpect(content().string(containsString("data-session-id=\"yellowstone-wolves\"")))
                .andExpect(content().string(containsString("data-api-base=\"/api/v1\"")))
                // Reading structure
                .andExpect(content().string(containsString("reading-bar")))
                .andExpect(content().string(containsString("id=\"timer\"")))
                .andExpect(content().string(containsString("reading-main")))
                .andExpect(content().string(containsString("id=\"passage\"")))
                .andExpect(content().string(containsString("id=\"hlBtn\"")))
                // Page stylesheet + module script are wired
                .andExpect(content().string(containsString("/css/reading.css")))
                .andExpect(content().string(containsString("type=\"module\"")))
                .andExpect(content().string(containsString("/js/reading.js")));
    }
}
