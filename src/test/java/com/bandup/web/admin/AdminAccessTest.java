package com.bandup.web.admin;

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
 * Verifies the admin area is deny-by-default: admin pages and the admin API are
 * blocked without the admin role, and the admin page renders its sidebar with
 * the active item when access is granted.
 */
@SpringBootTest(properties = "app.admin.dev-allow=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminAccessTest {

    @Autowired
    MockMvc mvc;

    @Test
    void adminPageDeniedWithoutRole() throws Exception {
        mvc.perform(get("/admin/sessions"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void adminApiDeniedWithoutRole() throws Exception {
        mvc.perform(get("/api/v1/admin/passages"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPageRendersSidebarWithRole() throws Exception {
        mvc.perform(get("/admin/sessions").sessionAttr(AdminAccess.SESSION_ROLE, Boolean.TRUE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("admin-sidebar")))
                .andExpect(content().string(containsString("Reading sessions")))
                // Sessions nav item marked active
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(containsString("/js/admin/sessions-page.js")));
    }

    @Test
    void publicNavbarShowsAdminEntryOnlyForAdmins() throws Exception {
        // Not admin: no Admin entry on the public page.
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">Admin</a>"))));
        // Admin: entry present.
        mvc.perform(get("/").sessionAttr(AdminAccess.SESSION_ROLE, Boolean.TRUE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Admin</a>")));
    }
}
