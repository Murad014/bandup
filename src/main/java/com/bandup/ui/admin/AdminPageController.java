package com.bandup.ui.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The admin page shells. They are public and hold no data: the browser loads
 * everything from the admin API, which only answers ADMIN and AUTHOR tokens
 * (backend NFR 2). The sign-in check in the browser is frontend task 4.
 */
@Controller
@RequestMapping("/admin")
public class AdminPageController {

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("activeKey", AdminNavigationAdvice.DASHBOARD);
        return "admin/index";
    }

    @GetMapping("/sessions")
    public String sessions(Model model) {
        model.addAttribute("activeKey", AdminNavigationAdvice.SESSIONS);
        return "admin/sessions";
    }

    @GetMapping("/sessions/new")
    public String newSession(Model model) {
        model.addAttribute("activeKey", AdminNavigationAdvice.SESSIONS);
        model.addAttribute("sessionId", "");
        return "admin/session-edit";
    }

    @GetMapping("/sessions/{slug}/edit")
    public String editSession(@PathVariable String slug, Model model) {
        model.addAttribute("activeKey", AdminNavigationAdvice.SESSIONS);
        model.addAttribute("sessionId", slug);
        return "admin/session-edit";
    }
}
