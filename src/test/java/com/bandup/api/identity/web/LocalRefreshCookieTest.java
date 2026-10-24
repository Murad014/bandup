package com.bandup.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bandup.TestcontainersConfiguration;

/**
 * Local development runs on http://localhost. Safari drops Secure cookies there
 * (Chrome doesn't), which made every page load sign the admin out again. So the
 * local profile sends the refresh cookie without Secure; everywhere else keeps it
 * ({@link AuthApiTest} checks the default).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class LocalRefreshCookieTest {

    @Autowired MockMvc mvc;

    @Test
    void theLocalProfileSendsTheRefreshCookieWithoutSecure() throws Exception {
        String cookie = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@a.az\", \"password\": \"admin\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(cookie).startsWith("bandup_refresh=").contains("Path=/api/v1/auth", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Secure");
    }
}
