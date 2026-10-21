package com.bandup.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the shared app shell renders the data-driven navbar:
 * brand, Reading as a working link, Grammar as a disabled "coming soon" entry,
 * and correct active-item marking for the current page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AppShellNavbarTest.ActiveReadingController.class)
class AppShellNavbarTest {

    @Autowired
    MockMvc mvc;

    @Test
    void homeRendersSharedNavbar() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                // Brand
                .andExpect(content().string(containsString("app-nav__brand")))
                .andExpect(content().string(containsString("Band<span>Up</span>")))
                // Reading is a working link
                .andExpect(content().string(containsString("href=\"/reading/yellowstone-wolves\"")))
                .andExpect(content().string(containsString(">Reading</a>")))
                // Grammar is disabled + coming soon
                .andExpect(content().string(containsString("app-nav__link--disabled")))
                .andExpect(content().string(containsString("app-nav__badge")))
                .andExpect(content().string(containsString(">soon</span>")));
    }

    @Test
    void activePageMarksNavItemCurrent() throws Exception {
        mvc.perform(get("/_test/reading"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("is-active")))
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }

    /** Test-only page that marks the Reading nav item active. */
    @Controller
    static class ActiveReadingController {
        @GetMapping("/_test/reading")
        String readingActive(Model model) {
            model.addAttribute("activeKey", "reading");
            return "index";
        }
    }
}
