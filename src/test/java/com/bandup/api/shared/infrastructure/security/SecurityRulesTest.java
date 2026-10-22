package com.bandup.api.shared.infrastructure.security;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.identity.application.port.AccessTokenIssuer;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.identity.domain.user.valueobject.UserStatus;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** The access rules of design §6.4, end to end through the real security filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SecurityRulesTestController.class})
class SecurityRulesTest {

    @Autowired MockMvc mvc;
    @Autowired AccessTokenIssuer issuer;
    @Autowired SecretKey jwtSigningKey;

    private final User learner = user(Role.LEARNER);
    private final User author = user(Role.AUTHOR);
    private final User admin = user(Role.ADMIN);

    // ------------------------------------------------------------ public

    @Test
    void publicEndpointsWorkWithoutAToken() throws Exception {
        ok(post("/api/v1/auth/login")).andExpect(content().string("anonymous"));
        ok(get("/api/v1/passages/yellowstone-wolves")).andExpect(content().string("anonymous"));
        ok(post("/api/v1/attempts")).andExpect(content().string("anonymous"));
    }

    @Test
    void publicEndpointsKnowTheCallerWhenATokenIsSent() throws Exception {
        ok(post("/api/v1/attempts").header("Authorization", bearer(learner)))
                .andExpect(content().string(learner.id() + "|LEARNER|az"));
    }

    // -------------------------------------------------------- signed in

    @Test
    void meNeedsAToken() throws Exception {
        unauthorized(get("/api/v1/auth/me"));
        ok(get("/api/v1/auth/me").header("Authorization", bearer(learner)))
                .andExpect(content().string(learner.id() + "|LEARNER|az"));
    }

    // ------------------------------------------------------------ admin

    @Test
    void adminApiNeedsAdminOrAuthor() throws Exception {
        unauthorized(get("/api/v1/admin/passages"));
        forbidden(get("/api/v1/admin/passages").header("Authorization", bearer(learner)));
        ok(get("/api/v1/admin/passages").header("Authorization", bearer(author)));
        ok(get("/api/v1/admin/passages").header("Authorization", bearer(admin)))
                .andExpect(content().string(admin.id() + "|ADMIN|en"));
    }

    @Test
    void everyOtherApiPathIsDenied() throws Exception {
        unauthorized(get("/api/v1/something-else"));
        forbidden(get("/api/v1/something-else").header("Authorization", bearer(admin)));
    }

    @Test
    void pagesAndStaticFilesAreNotBehindTheApiRules() throws Exception {
        // Pages are public shells (even admin ones): the API decides who sees data.
        mvc.perform(get("/reading/yellowstone-wolves")).andExpect(status().isOk());
        mvc.perform(get("/admin/sessions")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    // ------------------------------------------------------- bad tokens

    @Test
    void expiredTokenIsRejected() throws Exception {
        // Issued 1 hour ago with a 15-minute lifetime: well past the 60 s clock skew.
        String expired = issuer.issue(learner, Instant.now().minus(Duration.ofHours(1))).value();
        unauthorized(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = issuer.issue(learner, Instant.now()).value();
        String[] parts = token.split("\\.");
        // Change the payload (e.g. to claim another role) without re-signing.
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 2) + (parts[1].endsWith("A") ? "BB" : "AA");
        unauthorized(get("/api/v1/admin/passages").header("Authorization", "Bearer " + parts[0] + "." + tamperedPayload + "." + parts[2]));
        unauthorized(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.jwt"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        SecretKey otherKey = new SecretKeySpec("another-secret-another-secret-12".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        unauthorized(get("/api/v1/admin/passages").header("Authorization", "Bearer " + sign(otherKey, "bandup", Role.ADMIN)));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        unauthorized(get("/api/v1/admin/passages").header("Authorization", "Bearer " + sign(jwtSigningKey, "evil", Role.ADMIN)));
    }

    @Test
    void anInvalidTokenIsRejectedEvenOnPublicEndpoints() throws Exception {
        // A browser with a broken token should refresh it, not be treated as anonymous.
        unauthorized(post("/api/v1/attempts").header("Authorization", "Bearer not.a.jwt"));
    }

    @Test
    void theApiIsStateless() throws Exception {
        mvc.perform(probe(get("/api/v1/auth/me").header("Authorization", bearer(learner))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    // ---------------------------------------------------------- helpers

    private ResultActions ok(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(probe(request)).andExpect(status().isOk());
    }

    /** Routes the request to the test probe instead of a real controller on the same path. */
    private static MockHttpServletRequestBuilder probe(MockHttpServletRequestBuilder request) {
        return request.header(SecurityRulesTestController.PROBE_HEADER, "true");
    }

    private void unauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(probe(request))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Not signed in"));
    }

    private void forbidden(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(probe(request))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Not allowed"));
    }

    private String bearer(User user) {
        return "Bearer " + issuer.issue(user, Instant.now()).value();
    }

    private static String sign(SecretKey key, String issuer, Role role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(UserId.newId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("roles", List.of(role.name())).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static User user(Role role) {
        LanguageCode lang = role == Role.LEARNER ? LanguageCode.of("az") : LanguageCode.ENGLISH;
        return User.restore(UserId.newId(), Email.of(role.name().toLowerCase() + "@example.com"),
                new PasswordHash("$2a$12$x"), null, lang, lang, LanguageCode.ENGLISH, EnumSet.of(role), UserStatus.ACTIVE);
    }
}
