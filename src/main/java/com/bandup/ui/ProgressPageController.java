package com.bandup.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** /progress: "My progress" — a signed-in learner's attempts (progress.js, GET /api/v1/me/*). */
@Controller
public class ProgressPageController {

    public static final String PROGRESS = "progress";

    @GetMapping("/progress")
    public String progress(Model model) {
        model.addAttribute("activeKey", PROGRESS);
        return "progress";
    }
}
