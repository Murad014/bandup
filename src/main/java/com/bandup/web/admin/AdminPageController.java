package com.bandup.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the admin pages (behind {@link AdminAccessFilter}). Pages are
 * content-free server-side: the browser modules load data from the admin REST
 * API. Each handler sets {@code activeKey} for the sidebar.
 */
@Controller
@RequestMapping("/admin")
public class AdminPageController {

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("activeKey", AdminWebAdvice.DASHBOARD);
        return "admin/index";
    }

    @GetMapping("/sessions")
    public String sessions(Model model) {
        model.addAttribute("activeKey", AdminWebAdvice.SESSIONS);
        return "admin/sessions";
    }

    @GetMapping("/sessions/new")
    public String newSession(Model model) {
        model.addAttribute("activeKey", AdminWebAdvice.SESSIONS);
        model.addAttribute("sessionId", "");
        return "admin/session-edit";
    }

    @GetMapping("/sessions/{id}/edit")
    public String editSession(@PathVariable String id, Model model) {
        model.addAttribute("activeKey", AdminWebAdvice.SESSIONS);
        model.addAttribute("sessionId", id);
        return "admin/session-edit";
    }
}
