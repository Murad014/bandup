package com.bandup.api.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Every error the API can return is Problem Details with the right status.
 * Security filters are off here: this test is about error mapping; access rules
 * are tested in {@code SecurityRulesTest}.
 */
@WebMvcTest(controllers = ProblemsTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ProblemsTestController.class, ApiExceptionHandler.class})
class ApiExceptionHandlerTest {

    private static final String BASE = "/api/v1/_test/problems";

    @Autowired
    MockMvc mvc;

    @Test
    void ruleViolationsAre422WithFieldErrors() throws Exception {
        problem(get(BASE + "/domain"), 422, "Validation failed")
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("Title is required."))
                .andExpect(jsonPath("$.errors[1].field").value("answerKey[4].answer"));
    }

    @Test
    void invalidRequestIs400() throws Exception {
        problem(get(BASE + "/invalid"), 400, "Invalid request")
                .andExpect(jsonPath("$.detail").value("Unknown question number: 7"));
    }

    @Test
    void notFoundIs404() throws Exception {
        problem(get(BASE + "/not-found"), 404, "Not found")
                .andExpect(jsonPath("$.detail").value("No published passage: coral-reefs"));
    }

    @Test
    void conflictIs409() throws Exception {
        problem(get(BASE + "/conflict"), 409, "Conflict");
    }

    @Test
    void unauthenticatedIs401AndForbiddenIs403() throws Exception {
        problem(get(BASE + "/unauthenticated"), 401, "Not signed in");
        problem(get(BASE + "/forbidden"), 403, "Not allowed");
    }

    @Test
    void databaseConstraintIs409WithoutSql() throws Exception {
        problem(get(BASE + "/data-integrity"), 409, "Conflict")
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data."))
                .andExpect(content().string(not(containsString("exercise_slug_key"))));
    }

    @Test
    void unexpectedErrorIs500WithoutInternalDetails() throws Exception {
        problem(get(BASE + "/boom"), 500, "Something went wrong")
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void springMvcErrorsAreProblemDetailsToo() throws Exception {
        problemStatus(get(BASE + "/typed").param("n", "seven"), 400);
        problemStatus(get(BASE + "/typed"), 400);
        problemStatus(post(BASE + "/body").contentType(MediaType.APPLICATION_JSON).content("{ not json"), 400);
        problemStatus(post(BASE + "/domain"), 405);
        problemStatus(get("/api/v1/does-not-exist"), 404);
    }

    private ResultActions problem(org.springframework.test.web.servlet.RequestBuilder request, int status, String title)
            throws Exception {
        return problemStatus(request, status).andExpect(jsonPath("$.title").value(title));
    }

    private ResultActions problemStatus(org.springframework.test.web.servlet.RequestBuilder request, int status)
            throws Exception {
        return mvc.perform(request)
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }
}
