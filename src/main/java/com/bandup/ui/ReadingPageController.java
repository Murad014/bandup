package com.bandup.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * The reading page shell. It carries only the passage's slug; the browser
 * loads the passage from the learner API, which also answers 404 for a
 * passage that doesn't exist or isn't published.
 */
@Controller
public class ReadingPageController {

    @GetMapping("/reading/{slug}")
    public String reading(@PathVariable String slug, Model model) {
        model.addAttribute("activeKey", NavigationAdvice.READING);
        model.addAttribute("sessionId", slug);
        return "reading";
    }
}
