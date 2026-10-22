package com.bandup.api.content.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.content.application.AdminExerciseView;
import com.bandup.api.content.application.ExerciseAdmin;
import com.bandup.api.content.application.ExerciseInput;
import com.bandup.api.content.application.ExerciseSummary;

/**
 * The admin exercise API under {@code /api/v1/admin/passages} (design §8.4).
 * Only ADMIN and AUTHOR tokens get here (ContentAccessRules).
 */
@RestController
@RequestMapping("/api/v1/admin/passages")
class AdminExerciseController {

    private final ExerciseAdmin admin;

    AdminExerciseController(ExerciseAdmin admin) {
        this.admin = admin;
    }

    @GetMapping
    List<ExerciseSummary> list() {
        return admin.list();
    }

    @GetMapping("/{slug}")
    AdminExerciseView get(@PathVariable String slug) {
        return admin.get(slug);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdminExerciseView create(@RequestBody ExerciseInput body) {
        return admin.create(body);
    }

    @PutMapping("/{slug}")
    AdminExerciseView update(@PathVariable String slug, @RequestBody ExerciseInput body) {
        return admin.update(slug, body);
    }

    @PostMapping("/{slug}/publish")
    AdminExerciseView publish(@PathVariable String slug) {
        return admin.publish(slug);
    }

    @PostMapping("/{slug}/unpublish")
    AdminExerciseView unpublish(@PathVariable String slug) {
        return admin.unpublish(slug);
    }

    /** 204 either way; an exercise with attempts is archived instead of removed. */
    @DeleteMapping("/{slug}")
    ResponseEntity<Void> delete(@PathVariable String slug) {
        admin.delete(slug);
        return ResponseEntity.noContent().build();
    }
}
