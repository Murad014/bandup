package com.bandup.api.vocabulary.web;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.vocabulary.application.WordSessions;
import com.bandup.api.vocabulary.application.view.SessionViews.AnswerView;
import com.bandup.api.vocabulary.application.view.SessionViews.SessionView;

/** Learn and review sessions (vocabulary design §5.1); a token is required. */
@RestController
@RequestMapping("/api/v1/vocabulary/sessions")
@RequiredArgsConstructor
class WordSessionController {

    private final WordSessions sessions;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SessionView start(@RequestBody StartRequest body) {
        return sessions.start(body.kind(), body.exercise());
    }

    @GetMapping("/{id}")
    SessionView get(@PathVariable UUID id) {
        return sessions.get(id);
    }

    @PostMapping("/{id}/answers")
    AnswerView answer(@PathVariable UUID id, @RequestBody AnswerRequest body) {
        return sessions.answer(id, body.taskId(), body.answer());
    }

    record StartRequest(String kind, String exercise) {
    }

    record AnswerRequest(String taskId, Object answer) {
    }
}
