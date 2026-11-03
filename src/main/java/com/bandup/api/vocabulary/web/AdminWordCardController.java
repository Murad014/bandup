package com.bandup.api.vocabulary.web;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
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

import com.bandup.api.vocabulary.application.WordCardAdmin;
import com.bandup.api.vocabulary.application.view.AdminCardViews.Card;
import com.bandup.api.vocabulary.application.view.AdminCardViews.CardPage;

/**
 * The admin word card API under {@code /api/v1/admin/vocabulary/words}
 * (vocabulary design §5.2). Only ADMIN and AUTHOR tokens get here (the shared
 * {@code /api/v1/admin/**} rule).
 */
@RestController
@RequestMapping("/api/v1/admin/vocabulary/words")
@RequiredArgsConstructor
class AdminWordCardController {

    private final WordCardAdmin admin;

    @GetMapping
    CardPage list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return admin.list(status, q, page, size);
    }

    @GetMapping("/{id}")
    Card get(@PathVariable long id) {
        return admin.get(id);
    }

    @PutMapping("/{id}")
    Card update(@PathVariable long id, @RequestBody Card body) {
        return admin.update(id, body);
    }

    @PostMapping("/{id}/regenerate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Card regenerate(@PathVariable long id) {
        return admin.regenerate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        admin.delete(id);
    }
}
