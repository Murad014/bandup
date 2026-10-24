package com.bandup.api.shared.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.bandup.TestcontainersConfiguration;

/** Every response carries the browser protections (pages, API and images alike). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityHeadersTest {

    @Autowired MockMvc mvc;

    @Test
    void pagesAndApiCarryTheSecurityHeaders() throws Exception {
        for (String path : new String[] {"/", "/reading", "/admin", "/api/v1/exercises"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
        }
    }

    @Test
    void httpsRequestsAlsoGetStrictTransportSecurity() throws Exception {
        mvc.perform(get("/").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
        mvc.perform(get("/")).andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
