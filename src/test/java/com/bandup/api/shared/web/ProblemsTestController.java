package com.bandup.api.shared.web;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.ForbiddenException;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.application.UnauthenticatedException;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;

/** Test-only endpoints that fail in every way the API can fail. */
@RestController
@RequestMapping("/api/v1/_test/problems")
class ProblemsTestController {

    @GetMapping("/domain")
    void domain() {
        throw new DomainException(List.of(
                new Violation("title", "Title is required."),
                new Violation("answerKey[4].answer", "Q4 needs at least one accepted answer.")));
    }

    @GetMapping("/invalid")
    void invalid() {
        throw new InvalidRequestException("Unknown question number: 7");
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new NotFoundException("No published passage: coral-reefs");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException("Someone else changed this session. Reload to see their changes.");
    }

    @GetMapping("/unauthenticated")
    void unauthenticated() {
        throw new UnauthenticatedException("Your session has expired. Please sign in again.");
    }

    @GetMapping("/forbidden")
    void forbidden() {
        throw new ForbiddenException("Only authors can edit exercises.");
    }

    @GetMapping("/data-integrity")
    void dataIntegrity() {
        throw new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint \"exercise_slug_key\"");
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("secret internal detail");
    }

    @GetMapping("/typed")
    int typed(@RequestParam int n) {
        return n;
    }

    @PostMapping("/body")
    Map<String, Object> body(@RequestBody Map<String, Object> body) {
        return body;
    }
}
