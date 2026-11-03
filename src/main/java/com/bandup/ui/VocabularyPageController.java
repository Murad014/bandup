package com.bandup.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The vocabulary pages (vocabulary design §6). Shells only: words, cards and
 * sessions are fetched by the page scripts from /api/v1/vocabulary.
 */
@Controller
public class VocabularyPageController {

    /** /vocabulary: My words. */
    @GetMapping("/vocabulary")
    public String words(Model model) {
        model.addAttribute("activeKey", NavigationAdvice.VOCABULARY);
        return "vocabulary";
    }

    /** /vocabulary/learn: a learn session (?exercise= for one passage's words). */
    @GetMapping("/vocabulary/learn")
    public String learn(Model model) {
        model.addAttribute("activeKey", NavigationAdvice.VOCABULARY);
        model.addAttribute("kind", "learn");
        return "vocabulary-session";
    }

    /** /vocabulary/review: the daily review. */
    @GetMapping("/vocabulary/review")
    public String review(Model model) {
        model.addAttribute("activeKey", NavigationAdvice.VOCABULARY);
        model.addAttribute("kind", "review");
        return "vocabulary-session";
    }
}
