package com.bandup.api.shared.infrastructure.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.shared.application.CurrentUser;

/**
 * Test-only endpoints, one per access group of design §6.4. Each echoes the caller.
 * Every mapping requires the {@code X-Security-Probe} header, so it never clashes
 * with the real controller on the same path (component scanning picks this class
 * up in every test context).
 */
@RestController
class SecurityRulesTestController {

    static final String PROBE_HEADER = "X-Security-Probe";
    private static final String PROBE = PROBE_HEADER + "=true";

    private final CurrentUser currentUser;

    SecurityRulesTestController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @PostMapping(path = "/api/v1/auth/login", headers = PROBE)
    String login() {
        return caller();
    }

    @GetMapping(path = "/api/v1/auth/me", headers = PROBE)
    String me() {
        return caller();
    }

    @GetMapping(path = "/api/v1/passages/yellowstone-wolves", headers = PROBE)
    String passage() {
        return caller();
    }

    @PostMapping(path = "/api/v1/attempts", headers = PROBE)
    String attempt() {
        return caller();
    }

    @GetMapping(path = "/api/v1/admin/passages", headers = PROBE)
    String admin() {
        return caller();
    }

    @GetMapping(path = "/api/v1/something-else", headers = PROBE)
    String other() {
        return caller();
    }

    private String caller() {
        return currentUser.get()
                .map(c -> c.userId() + "|" + String.join(",", new java.util.TreeSet<>(c.roles())) + "|"
                        + c.supportLanguage().map(Object::toString).orElse("-"))
                .orElse("anonymous");
    }
}
