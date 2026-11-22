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

    /** /vocabulary/sets: the learner's sets of learned words (requirement 6.3). */
    @GetMapping("/vocabulary/sets")
    public String sets(Model model) {
        model.addAttribute("activeKey", NavigationAdvice.VOCABULARY);
        return "vocabulary-sets";
    }

    /** /vocabulary/round: one round of the current set, Azerbaijani → English (requirement 6.4). */
    @GetMapping("/vocabulary/round")
    public String round(Model model) {
        model.addAttribute("activeKey", NavigationAdvice.VOCABULARY);
        model.addAttribute("kind", "round");
        return "vocabulary-session";
    }

    /** The daily review and Recall were replaced by sets (decision 15); old links land there. */
    @GetMapping({"/vocabulary/review", "/vocabulary/recall"})
    public String replacedBySets() {
        return "redirect:/vocabulary/sets";
    }
}
