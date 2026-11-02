package com.bandup.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * The grammar pages (grammar design §6). Shells only: the level, the course
 * and its lessons are fetched by the page's script from /api/v1/grammar.
 */
@Controller
public class GrammarPageController {

    /** /grammar: the level choice the first time, then the course. */
    @GetMapping("/grammar")
    public String course(Model model) {
        model.addAttribute("activeKey", NavigationAdvice.GRAMMAR);
        return "grammar";
    }

    /** /grammar/lessons/{slug}: one lesson; only its id is in the page. */
    @GetMapping("/grammar/lessons/{slug}")
    public String lesson(@PathVariable String slug, Model model) {
        model.addAttribute("activeKey", NavigationAdvice.GRAMMAR);
        model.addAttribute("slug", slug);
        return "grammar-lesson";
    }

    /** /grammar/review: the daily review; the cards come from the API (signed in only). */
    @GetMapping("/grammar/review")
    public String review(Model model) {
        model.addAttribute("activeKey", NavigationAdvice.GRAMMAR);
        return "grammar-review";
    }
}
