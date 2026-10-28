package com.bandup.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The sign-in, sign-up and account-email page shells; the forms talk to /api/v1/auth through auth.js. */
@Controller
public class AuthPageController {

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("activeKey", "login");
        return "login";
    }

    /** Opened from the confirmation email; the page sends the link's token to the API. */
    @GetMapping("/verify-email")
    public String verifyEmail(Model model) {
        model.addAttribute("activeKey", "login");
        return "verify-email";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        model.addAttribute("activeKey", "login");
        return "forgot-password";
    }

    /** Opened from the reset email. */
    @GetMapping("/reset-password")
    public String resetPassword(Model model) {
        model.addAttribute("activeKey", "login");
        return "reset-password";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("activeKey", "register");
        return "register";
    }
}
