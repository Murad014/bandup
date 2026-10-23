package com.bandup.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.bandup.TestcontainersConfiguration;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/**
 * Register, login, refresh, logout and me, end to end (requirement 8). No
 * test-wide transaction: refresh must really commit its revocations even when
 * it answers 401, and this test checks exactly that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @BeforeEach
    void cleanAccounts() {
        jdbc.update("delete from login_throttle"); // all tests sign in from the same test address
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from app_user");
    }

    // ------------------------------------------------------------ register

    @Test
    void registerThenLoginThenMe() throws Exception {
        String id = registerAysel()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("LEARNER"))
                .andExpect(jsonPath("$.displayName").value("Aysel"))
                .andExpect(jsonPath("$.supportLanguage").value("az"))
                .andExpect(jsonPath("$.targetLanguage").value("en"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        MvcResult login = login("Aysel@Example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThan(890), org.hamcrest.Matchers.lessThanOrEqualTo(900))))
                .andExpect(jsonPath("$.user.id").value(id))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.roles[0]").value("LEARNER"));
    }

    @Test
    void registrationReportsEveryInvalidFieldAtOnce() throws Exception {
        register(Map.of("email", "not-an-email", "password", "short", "supportLanguage", "klingon"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "email", "password", "supportLanguage")));
    }

    /** Regression (found by the sign-up page in Chrome): the name was only checked after everything else. */
    @Test
    void aTooLongDisplayNameIsReportedTogetherWithTheOtherFields() throws Exception {
        register(Map.of("email", "not-an-email", "password", "short", "displayName", "x".repeat(81)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "email", "password", "displayName")));
    }

    @Test
    void anEmailCanOnlyBeRegisteredOnce() throws Exception {
        registerAysel().andExpect(status().isCreated());
        register(Map.of("email", "AYSEL@example.com", "password", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists."));
    }

    // --------------------------------------------------------------- login

    @Test
    void wrongEmailAndWrongPasswordGetTheSameAnswer() throws Exception {
        registerAysel();
        String wrongPassword = login("aysel@example.com", "wrong password!!").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrongEmail = login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String malformedEmail = login("not-an-email", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongEmail).isEqualTo(wrongPassword).isEqualTo(malformedEmail).contains("Wrong email or password.");
    }

    @Test
    void aDisabledUserCannotLoginOrRefresh() throws Exception {
        registerAysel();
        String cookie = refreshCookie(login("aysel@example.com", PASSWORD).andReturn());
        jdbc.update("update app_user set status = 'DISABLED' where email = 'aysel@example.com'");

        login("aysel@example.com", PASSWORD).andExpect(status().isUnauthorized());
        refresh(cookie).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null", Integer.class)).isZero();
    }

    @Test
    void theRefreshCookieIsLockedDown() throws Exception {
        registerAysel();
        String setCookie = login("aysel@example.com", PASSWORD).andReturn().getResponse().getHeader("Set-Cookie");

        assertThat(setCookie)
                .startsWith("bandup_refresh=")
                .contains("Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Strict");
        Matcher maxAge = Pattern.compile("Max-Age=(\\d+)").matcher(setCookie);
        assertThat(maxAge.find()).isTrue();
        assertThat(Long.parseLong(maxAge.group(1))).isBetween(14L * 24 * 3600 - 10, 14L * 24 * 3600);
        // Only the hash is stored.
        String raw = setCookie.substring("bandup_refresh=".length(), setCookie.indexOf(';'));
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where token_hash = ?", Integer.class, raw)).isZero();
    }

    // ------------------------------------------------------------- refresh

    @Test
    void refreshRotatesTheCookieAndGivesANewAccessToken() throws Exception {
        registerAysel();
        String first = refreshCookie(login("aysel@example.com", PASSWORD).andReturn());

        MvcResult refreshed = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.supportLanguage").value("az")).andReturn();
        String second = refreshCookie(refreshed);

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken(refreshed)))
                .andExpect(status().isOk());
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void reusingAnOldRefreshTokenEndsTheWholeSession() throws Exception {
        registerAysel();
        String first = refreshCookie(login("aysel@example.com", PASSWORD).andReturn());
        String second = refreshCookie(refresh(first).andReturn());
        String third = refreshCookie(refresh(second).andReturn()); // the real user keeps going

        // Someone replays the first (copied) token.
        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(jsonPath("$.title").value("Not signed in"));

        // The revocation was committed despite the 401: the real user's newest token is dead too.
        refresh(third).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null", Integer.class)).isZero();
    }

    /** The browser left the page mid-refresh and never stored the new cookie: sending the old one again still works. */
    @Test
    void aRefreshWhoseReplyWasLostCanBeRepeated() throws Exception {
        registerAysel();
        String first = refreshCookie(login("aysel@example.com", PASSWORD).andReturn());
        refresh(first).andExpect(status().isOk()); // reply lost: its cookie never reached the browser

        String again = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());

        refresh(again).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ login limits

    @Test
    void fiveWrongPasswordsLockTheEmailEvenForTheRightPassword() throws Exception {
        registerAysel();
        for (int i = 0; i < 5; i++) {
            login("aysel@example.com", "wrong-password-" + i).andExpect(status().isUnauthorized());
        }

        login("aysel@example.com", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("8\\d\\d|900")))
                .andExpect(jsonPath("$.detail").value("Too many sign-in attempts. Try again in 15 minutes."));
        // The lock is for that email only.
        register(Map.of("email", "leyla@example.com", "password", PASSWORD, "displayName", "Leyla"));
        login("leyla@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anEmailWithoutAnAccountIsLockedTheSameWaySoNothingIsRevealed() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("nobody@example.com", "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login("nobody@example.com", "wrong-password-x").andExpect(status().isTooManyRequests());
    }

    @Test
    void aSuccessfulSignInClearsTheCount() throws Exception {
        registerAysel();
        for (int i = 0; i < 4; i++) {
            login("aysel@example.com", "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login("aysel@example.com", PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("aysel@example.com", "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login("aysel@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void oneMachineTryingManyEmailsIsLockedToo() throws Exception {
        registerAysel();
        for (int i = 0; i < 30; i++) {
            loginFrom("10.0.0.7", "user" + i + "@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }
        loginFrom("10.0.0.7", "aysel@example.com", PASSWORD).andExpect(status().isTooManyRequests());
        loginFrom("10.0.0.8", "aysel@example.com", PASSWORD).andExpect(status().isOk()); // another machine is fine
    }

    private ResultActions loginFrom(String address, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .with(request -> { request.setRemoteAddr(address); return request; })
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    @Test
    void refreshWithoutAValidCookieIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        refresh("made-up-token").andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredAccessTokenDoesNotBlockTheRefresh() throws Exception {
        registerAysel();
        String cookie = refreshCookie(login("aysel@example.com", PASSWORD).andReturn());

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, cookie))
                        .header("Authorization", "Bearer an.expired.token"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------- logout

    @Test
    void logoutClearsTheCookieAndEndsTheSession() throws Exception {
        registerAysel();
        String cookie = refreshCookie(login("aysel@example.com", PASSWORD).andReturn());

        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(RefreshCookie.NAME, cookie)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        refresh(cookie).andExpect(status().isUnauthorized());

        // Idempotent: logging out again or without a cookie is fine.
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(RefreshCookie.NAME, cookie))).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ me

    @Test
    void meNeedsAValidTokenOfAnActiveUser() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        registerAysel();
        String token = accessToken(login("aysel@example.com", PASSWORD).andReturn());
        jdbc.update("update app_user set status = 'DELETED' where email = 'aysel@example.com'");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- helpers

    private ResultActions registerAysel() throws Exception {
        return register(Map.of("email", "Aysel@Example.com", "password", PASSWORD, "displayName", "Aysel",
                "uiLanguage", "az", "supportLanguage", "az"));
    }

    private ResultActions register(Map<String, String> body) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private ResultActions refresh(String cookie) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, cookie)));
    }

    private String accessToken(MvcResult result) throws Exception {
        return (String) json.readValue(result.getResponse().getContentAsString(), Map.class).get("accessToken");
    }

    private static String refreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        return setCookie.substring("bandup_refresh=".length(), setCookie.indexOf(';'));
    }
}
