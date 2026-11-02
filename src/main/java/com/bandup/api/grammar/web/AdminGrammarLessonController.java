package com.bandup.api.grammar.web;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.grammar.application.AdminLessonView;
import com.bandup.api.grammar.application.LessonAdmin;
import com.bandup.api.grammar.application.LessonInput;
import com.bandup.api.grammar.application.LessonSummary;

/**
 * The admin grammar API under {@code /api/v1/admin/grammar/lessons} (grammar
 * design §5.2). Only ADMIN and AUTHOR tokens get here (the shared
 * {@code /api/v1/admin/**} rule).
 */
@RestController
@RequestMapping("/api/v1/admin/grammar/lessons")
@RequiredArgsConstructor
class AdminGrammarLessonController {

    private final LessonAdmin admin;

    @GetMapping
    List<LessonSummary> list(@RequestParam(required = false) String level,
            @RequestParam(required = false) String status) {
        return admin.list(level, status);
    }

    @GetMapping("/{slug}")
    AdminLessonView get(@PathVariable String slug) {
        return admin.get(slug);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdminLessonView create(@RequestBody LessonInput body) {
        return admin.create(body);
    }

    @PutMapping("/{slug}")
    AdminLessonView update(@PathVariable String slug, @RequestBody LessonInput body) {
        return admin.update(slug, body);
    }

    /** Body optional: {@code {"position": 3}}; without it the lesson goes to the end of its course. */
    @PostMapping("/{slug}/publish")
    AdminLessonView publish(@PathVariable String slug, @RequestBody(required = false) PublishRequest body) {
        return admin.publish(slug, body == null ? null : body.position());
    }

    @PostMapping("/{slug}/unpublish")
    AdminLessonView unpublish(@PathVariable String slug) {
        return admin.unpublish(slug);
    }

    /** 204 either way; a lesson learners have used is archived instead of removed. */
    @DeleteMapping("/{slug}")
    ResponseEntity<Void> delete(@PathVariable String slug) {
        admin.delete(slug);
        return ResponseEntity.noContent().build();
    }

    record PublishRequest(Integer position) {
    }
}
