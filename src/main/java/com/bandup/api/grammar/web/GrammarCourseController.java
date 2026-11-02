package com.bandup.api.grammar.web;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.grammar.application.GrammarCourse;
import com.bandup.api.grammar.application.LessonRuns;
import com.bandup.api.grammar.application.view.LearnerViews.AnswerView;
import com.bandup.api.grammar.application.view.LearnerViews.CourseView;
import com.bandup.api.grammar.application.view.LearnerViews.LessonView;
import com.bandup.api.grammar.application.view.LearnerViews.LevelView;
import com.bandup.api.grammar.application.view.LearnerViews.ProgressView;
import com.bandup.api.grammar.application.view.LearnerViews.RunView;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.web.RequestLanguage;

/** The learner grammar API: levels, course, chosen level, lessons and runs (grammar design §5.1). */
@RestController
@RequestMapping("/api/v1/grammar")
@RequiredArgsConstructor
class GrammarCourseController {

    private final GrammarCourse course;
    private final LessonRuns runs;
    private final CurrentUser currentUser;

    @GetMapping("/levels")
    List<LevelView> levels() {
        return course.levels();
    }

    @GetMapping("/course")
    CourseView course(@RequestParam String level, @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return course.course(level, topic, page, size, language(lang, acceptLanguage));
    }

    @GetMapping("/me")
    ProgressView me() {
        return course.me();
    }

    @PutMapping("/me/level")
    ResponseEntity<Void> chooseLevel(@RequestBody LevelRequest body) {
        course.chooseLevel(body.level());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lessons/{slug}")
    LessonView lesson(@PathVariable String slug, @RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return course.lesson(slug, language(lang, acceptLanguage));
    }

    @PostMapping("/lessons/{slug}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    RunView start(@PathVariable String slug, @RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return runs.start(slug, language(lang, acceptLanguage));
    }

    @PostMapping("/runs/{runId}/answers")
    AnswerView answer(@PathVariable UUID runId, @RequestBody AnswerRequest body,
            @RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return runs.answer(runId, body.itemId(), body.answer(), language(lang, acceptLanguage));
    }

    private String language(String lang, String acceptLanguage) {
        Optional<String> userLanguage = currentUser.get().flatMap(c -> c.supportLanguage()).map(code -> code.value());
        return RequestLanguage.pick(lang, userLanguage, acceptLanguage);
    }

    record LevelRequest(String level) {
    }

    /** @param answer an option number (choose), a text (type, fix) or the tiles in order (build) */
    record AnswerRequest(String itemId, Object answer) {
    }
}
