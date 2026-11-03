package com.bandup.api.vocabulary.web;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.application.MyWords;
import com.bandup.api.vocabulary.application.SaveWord;
import com.bandup.api.vocabulary.application.WordLookup;
import com.bandup.api.vocabulary.application.view.VocabularyViews.CardView;
import com.bandup.api.vocabulary.application.view.VocabularyViews.SaveResult;
import com.bandup.api.vocabulary.application.view.VocabularyViews.SavedWordView;
import com.bandup.api.vocabulary.application.view.VocabularyViews.WordsPage;

/** The learner's words (vocabulary design §5.1). Everything but {@code /lookup} needs a token. */
@RestController
@RequestMapping("/api/v1/vocabulary")
@RequiredArgsConstructor
class WordController {

    private final SaveWord saveWord;
    private final MyWords myWords;
    private final WordLookup lookup;

    @GetMapping("/lookup")
    CardView lookup(@RequestParam String text) {
        return lookup.lookup(text);
    }

    /** 201 for a newly saved word, 200 when the learner had it already. */
    @PostMapping("/words")
    ResponseEntity<SaveResult> save(@RequestBody SaveRequest body) {
        SaveResult result = saveWord.save(body.text(), body.source() == null ? null : body.source().exercise(),
                body.source() == null ? null : body.source().sentence());
        if (result.alreadySaved()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.created(URI.create("/api/v1/vocabulary/words/" + result.word().id())).body(result);
    }

    /** A page of words, or (with {@code ids}) just those words for polling. */
    @GetMapping("/words")
    Object words(@RequestParam(required = false) String state, @RequestParam(required = false) String q,
            @RequestParam(required = false) String exercise, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) String ids) {
        if (ids != null) {
            return byIds(ids);
        }
        return pageOf(state, q, exercise, page, size);
    }

    private WordsPage pageOf(String state, String q, String exercise, int page, int size) {
        return myWords.page(state, q, exercise, page, size);
    }

    private List<SavedWordView> byIds(String ids) {
        try {
            return myWords.byIds(Arrays.stream(ids.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                    .map(UUID::fromString).distinct().toList());
        } catch (IllegalArgumentException e) {
            throw DomainException.of("ids", "Give word ids separated by commas.");
        }
    }

    @DeleteMapping("/words/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID id) {
        myWords.remove(id);
    }

    @PostMapping("/words/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void retry(@PathVariable UUID id) {
        myWords.retry(id);
    }

    record SaveRequest(String text, Source source) {
    }

    record Source(String exercise, String sentence) {
    }
}
