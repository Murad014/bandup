package com.bandup.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.bandup.web.layout.NavigationAdvice;

/**
 * Serves the Reading practice page shell. The page is intentionally content-free
 * server-side: it carries only the session id and the API base path. The browser
 * module (reading.js) loads the session from the REST API and renders it.
 */
@Controller
public class ReadingPageController {

    @GetMapping("/reading/{id}")
    public String reading(@PathVariable String id, Model model) {
        model.addAttribute("activeKey", NavigationAdvice.READING);
        model.addAttribute("sessionId", id);
        return "reading";
    }
}
