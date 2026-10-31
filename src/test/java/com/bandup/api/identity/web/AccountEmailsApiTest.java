package com.bandup.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bandup.RecordingMailer;
import com.bandup.TestcontainersConfiguration;

import tools.jackson.databind.json.JsonMapper;

/** Requirement 12: confirmation emails, password reset, Instructor requests — through the API. */
@SpringBootTest(properties = {"bandup.auth.admin-email=owner@bandup.test", "bandup.auth.admin-password=owner-password-1",
        "bandup.auth.public-url=https://bandup.test/"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountEmailsApiTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired RecordingMailer mailbox;

    @BeforeEach
    void clean() {
        jdbc.update("delete from login_throttle");
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from app_user where email <> 'owner@bandup.test'");
        mailbox.clear();
    }

    @Test
    void aNewAccountConfirmsItsEmailBeforeSigningIn() throws Exception {
        register("aysel@example.com", "LEARNER").andExpect(status().isCreated())
                .andExpect(jsonPath("$.instructorStatus").value("NONE"));

        assertThat(mailbox.to("aysel@example.com")).singleElement()
                .satisfies(m -> assertThat(m.subject()).isEqualTo("Confirm your Orbiko email"))
                .satisfies(m -> assertThat(m.text()).contains("https://bandup.test/verify-email?token=", "24 hours", "Hello Aysel"));
        login("aysel@example.com", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.detail").value("Please confirm your email first: open the link we sent you."));
        // A wrong password still says only "wrong email or password".
        login("aysel@example.com", "wrong-password-1").andExpect(status().isUnauthorized());

        String token = mailbox.latestToken("aysel@example.com", "/verify-email").orElseThrow();
        post("/api/v1/auth/verify-email", Map.of("token", token)).andExpect(status().isOk());
        login("aysel@example.com", PASSWORD).andExpect(status().isOk());

        // A link works once.
        post("/api/v1/auth/verify-email", Map.of("token", token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("token"));
        post("/api/v1/auth/verify-email", Map.of("token", "made-up")).andExpect(status().isBadRequest());
        post("/api/v1/auth/verify-email", Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void aNewLinkReplacesTheOldOneAtMostOnceAMinuteAndAnswersRevealNothing() throws Exception {
        register("aysel@example.com", "LEARNER");
        String first = mailbox.latestToken("aysel@example.com", "/verify-email").orElseThrow();

        post("/api/v1/auth/resend-verification", Map.of("email", "aysel@example.com")).andExpect(status().isAccepted());
        assertThat(mailbox.to("aysel@example.com")).hasSize(1); // less than a minute after the first

        jdbc.update("update account_token set issued_at = issued_at - interval '2 minutes'");
        post("/api/v1/auth/resend-verification", Map.of("email", "AYSEL@example.com")).andExpect(status().isAccepted());
        assertThat(mailbox.to("aysel@example.com")).hasSize(2);
        String second = mailbox.latestToken("aysel@example.com", "/verify-email").orElseThrow();
        assertThat(second).isNotEqualTo(first);

        post("/api/v1/auth/verify-email", Map.of("token", first)).andExpect(status().isBadRequest()); // replaced
        post("/api/v1/auth/verify-email", Map.of("token", second)).andExpect(status().isOk());

        // Same answer for unknown, malformed and already-confirmed emails; nothing is sent.
        for (String email : new String[] {"nobody@example.com", "not-an-email", "aysel@example.com"}) {
            post("/api/v1/auth/resend-verification", Map.of("email", email)).andExpect(status().isAccepted());
        }
        assertThat(mailbox.to("aysel@example.com")).hasSize(2);
        assertThat(mailbox.to("nobody@example.com")).isEmpty();
    }

    @Test
    void anExpiredConfirmationLinkDoesntWork() throws Exception {
        register("aysel@example.com", "LEARNER");
        String token = mailbox.latestToken("aysel@example.com", "/verify-email").orElseThrow();
        jdbc.update("update account_token set issued_at = now() - interval '25 hours', expires_at = now() - interval '1 hour'");
        post("/api/v1/auth/verify-email", Map.of("token", token)).andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordSetsANewOneConfirmsTheEmailAndSignsOutEverywhere() throws Exception {
        register("aysel@example.com", "LEARNER");
        post("/api/v1/auth/verify-email", Map.of("token", mailbox.latestToken("aysel@example.com", "/verify-email").orElseThrow()));
        MvcResult session = login("aysel@example.com", PASSWORD).andExpect(status().isOk()).andReturn();
        String cookie = session.getResponse().getHeader("Set-Cookie").split(";")[0];

        post("/api/v1/auth/forgot-password", Map.of("email", "aysel@example.com")).andExpect(status().isAccepted());
        post("/api/v1/auth/forgot-password", Map.of("email", "nobody@example.com")).andExpect(status().isAccepted());
        assertThat(mailbox.to("nobody@example.com")).isEmpty();
        var reset = mailbox.to("aysel@example.com").getLast();
        assertThat(reset.subject()).isEqualTo("Reset your Orbiko password");
        assertThat(reset.text()).contains("https://bandup.test/reset-password?token=", "1 hour");
        String token = mailbox.latestToken("aysel@example.com", "/reset-password").orElseThrow();

        // A weak password is a field error, and the link still works afterwards.
        post("/api/v1/auth/reset-password", Map.of("token", token, "password", "short")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        post("/api/v1/auth/reset-password", Map.of("token", token, "password", "a brand new password")).andExpect(status().isOk());

        login("aysel@example.com", PASSWORD).andExpect(status().isUnauthorized());
        login("aysel@example.com", "a brand new password").andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                .header("Cookie", cookie)).andExpect(status().isUnauthorized()); // the old session is gone
        post("/api/v1/auth/reset-password", Map.of("token", token, "password", "another new password"))
                .andExpect(status().isBadRequest()); // once only
    }

    @Test
    void aResetLinkAlsoConfirmsAnUnconfirmedEmail() throws Exception {
        register("aysel@example.com", "LEARNER"); // never opened the confirmation link
        post("/api/v1/auth/forgot-password", Map.of("email", "aysel@example.com"));
        String token = mailbox.latestToken("aysel@example.com", "/reset-password").orElseThrow();
        post("/api/v1/auth/reset-password", Map.of("token", token, "password", "a brand new password")).andExpect(status().isOk());
        login("aysel@example.com", "a brand new password").andExpect(status().isOk());

        // A confirmation link can't be used as a reset link.
        register("leyla@example.com", "LEARNER");
        String confirmation = mailbox.latestToken("leyla@example.com", "/verify-email").orElseThrow();
        post("/api/v1/auth/reset-password", Map.of("token", confirmation, "password", "a brand new password"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anInstructorWaitsForAnAdminWhoApprovesThemIntoAnAuthor() throws Exception {
        register("leyla@example.com", "INSTRUCTOR").andExpect(status().isCreated())
                .andExpect(jsonPath("$.instructorStatus").value("PENDING"))
                .andExpect(jsonPath("$.roles[0]").value("LEARNER"));
        register("rasim@example.com", "INSTRUCTOR");
        register("x@example.com", "TEACHER").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("accountType"));
        confirm("leyla@example.com");
        String leyla = bearer(login("leyla@example.com", PASSWORD).andReturn());
        mvc.perform(get("/api/v1/admin/passages").header("Authorization", leyla)).andExpect(status().isForbidden());

        String admin = bearer(login("owner@bandup.test", "owner-password-1").andExpect(status().isOk()).andReturn());
        String body = mvc.perform(get("/api/v1/admin/instructor-requests").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("leyla@example.com"))
                .andExpect(jsonPath("$[0].emailConfirmed").value(true))
                .andExpect(jsonPath("$[1].emailConfirmed").value(false))
                .andReturn().getResponse().getContentAsString();
        String leylaId = idOf(body, "leyla@example.com");
        String rasimId = idOf(body, "rasim@example.com");

        // Only an admin: not the instructor, not anyone without a token.
        mvc.perform(get("/api/v1/admin/instructor-requests").header("Authorization", leyla)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/instructor-requests")).andExpect(status().isUnauthorized());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/admin/instructor-requests/" + leylaId + "/approve").header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/admin/instructor-requests/" + leylaId + "/approve").header("Authorization", admin))
                .andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/admin/instructor-requests/" + rasimId + "/reject").header("Authorization", admin))
                .andExpect(status().isNoContent());
        assertThat(mailbox.to("leyla@example.com").getLast().subject()).isEqualTo("You're now an Orbiko Instructor");
        assertThat(mailbox.to("rasim@example.com")).hasSize(1); // just the confirmation

        String leylaAgain = bearer(login("leyla@example.com", PASSWORD).andReturn());
        mvc.perform(get("/api/v1/admin/passages").header("Authorization", leylaAgain)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/instructor-requests").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------ helpers

    private ResultActions register(String email, String accountType) throws Exception {
        String name = Character.toUpperCase(email.charAt(0)) + email.substring(1, email.indexOf('@'));
        return post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", name,
                "accountType", accountType));
    }

    private void confirm(String email) throws Exception {
        post("/api/v1/auth/verify-email", Map.of("token", mailbox.latestToken(email, "/verify-email").orElseThrow()))
                .andExpect(status().isOk());
    }

    private ResultActions login(String email, String password) throws Exception {
        return post("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    private ResultActions post(String url, Map<String, String> body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private String idOf(String requestsJson, String email) {
        for (Object o : json.readValue(requestsJson, java.util.List.class)) {
            Map<?, ?> request = (Map<?, ?>) o;
            if (email.equals(request.get("email"))) {
                return (String) request.get("userId");
            }
        }
        throw new AssertionError("No request for " + email);
    }

    private String bearer(MvcResult login) throws Exception {
        return "Bearer " + json.readValue(login.getResponse().getContentAsString(), Map.class).get("accessToken");
    }
}
