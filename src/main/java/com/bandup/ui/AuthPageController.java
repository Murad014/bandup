package com.bandup.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The sign-in and sign-up page shells; the forms talk to /api/v1/auth through auth.js. */
@Controller
public class AuthPageController {

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("activeKey", "login");
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("activeKey", "register");
        return "register";
    }
}
