package com.bandup.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page. Primarily exists so the shared app shell + navbar has a page to
 * render; feature pages (Reading, later Grammar) plug into the same layout.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("activeKey", "home");
        return "index";
    }
}
