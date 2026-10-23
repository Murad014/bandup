package com.bandup.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** /reading: the catalog of reading exercises (filled by catalog.js from GET /api/v1/exercises). */
@Controller
public class CatalogPageController {

    @GetMapping("/reading")
    public String catalog(Model model) {
        model.addAttribute("activeKey", NavigationAdvice.READING);
        return "catalog";
    }
}
