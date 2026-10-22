package com.bandup.api.practice.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.practice.application.SubmitAttempt;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.web.RequestLanguage;

/**
 * {@code POST /api/v1/attempts}: grades and stores an attempt (public; a
 * valid token links it to the learner).
 */
@RestController
@RequestMapping("/api/v1/attempts")
class AttemptController {

    /**
     * @param passageId the exercise slug
     * @param answers   {@code value} is a string, a list of option keys ("Choose N") or null (unanswered)
     */
    record AttemptRequest(String passageId, Integer elapsedSeconds, List<AnswerRequest> answers) {
    }

    record AnswerRequest(Integer n, Object value) {
    }

    private final SubmitAttempt submitAttempt;
    private final CurrentUser currentUser;

    AttemptController(SubmitAttempt submitAttempt, CurrentUser currentUser) {
        this.submitAttempt = submitAttempt;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AttemptResponse submit(@RequestBody AttemptRequest body, @RequestParam(required = false) String lang,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        if (body.passageId() == null || body.passageId().isBlank()) {
            throw new InvalidRequestException("The submission is invalid.",
                    List.of(new Violation("passageId", "The passage is required.")));
        }
        Optional<String> userLanguage = currentUser.get().flatMap(c -> c.supportLanguage()).map(code -> code.value());
        return AttemptResponse.of(submitAttempt.submit(new SubmitAttempt.Command(body.passageId(),
                body.elapsedSeconds(), answers(body.answers()), RequestLanguage.pick(lang, userLanguage, acceptLanguage))));
    }

    private static List<SubmitAttempt.Answer> answers(List<AnswerRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        List<SubmitAttempt.Answer> answers = new ArrayList<>();
        List<Violation> problems = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            AnswerRequest request = requests.get(i);
            Object value = request == null ? null : request.value();
            Integer n = request == null ? null : request.n();
            if (value == null) {
                answers.add(new SubmitAttempt.Answer(n, null, null));
            } else if (value instanceof String text) {
                answers.add(new SubmitAttempt.Answer(n, text, null));
            } else if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
                answers.add(new SubmitAttempt.Answer(n, null, list.stream().map(String.class::cast).toList()));
            } else {
                problems.add(new Violation("answers[" + i + "].value", "An answer is a text or a list of letters."));
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidRequestException("The submission is invalid.", problems);
        }
        return answers;
    }
}
