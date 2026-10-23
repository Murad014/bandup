package com.bandup.api.content.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;

import tools.jackson.databind.json.JsonMapper;

/** Cover images: upload (authors), serve (everyone), use on an exercise, show in the catalog and passage. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CoverImageApiTest {

    @Autowired MockMvc mvc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JsonMapper json;

    @Test
    void anAuthorUploadsAnImageAndEveryoneCanSeeIt() throws Exception {
        byte[] png = image("png", 40, 30);
        MvcResult uploaded = mvc.perform(multipart("/api/v1/admin/media").file(new MockMultipartFile("file", "cover.png", "image/png", png))
                        .header("Authorization", bearer("AUTHOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.url").value(startsWith("/media/")))
                .andExpect(jsonPath("$.width").value(40))
                .andExpect(jsonPath("$.height").value(30))
                .andReturn();
        String url = (String) json.readValue(uploaded.getResponse().getContentAsString(), Map.class).get("url");

        byte[] served = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(png);

        mvc.perform(multipart("/api/v1/admin/media").file(new MockMultipartFile("file", "c.jpg", "image/jpeg", image("jpg", 8, 8)))
                .header("Authorization", bearer("ADMIN"))).andExpect(status().isCreated());
    }

    @Test
    void onlyRealJpegOrPngImagesAreAccepted() throws Exception {
        upload("fake.png", "image/png", "<script>alert(1)</script>".getBytes())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value("This file isn't a readable JPEG or PNG image."));
        upload("anim.gif", "image/gif", image("gif", 4, 4))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Use a JPEG or PNG image."));
        upload("empty.png", "image/png", new byte[0]).andExpect(status().isUnprocessableContent());
        // Over 3 MB: the app's own check (MockMvc has no server upload limit; the real server answers 413 first).
        upload("huge.png", "image/png", new byte[3 * 1024 * 1024 + 1])
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("The image is too large (at most 3 MB)."));
    }

    @Test
    void uploadingNeedsAnAuthorAndUnknownImagesAre404() throws Exception {
        mvc.perform(multipart("/api/v1/admin/media").file(new MockMultipartFile("file", "c.png", "image/png", image("png", 4, 4))))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/admin/media").file(new MockMultipartFile("file", "c.png", "image/png", image("png", 4, 4)))
                .header("Authorization", bearer("LEARNER"))).andExpect(status().isForbidden());
        mvc.perform(get("/media/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/media/not-an-id")).andExpect(status().isNotFound());
        mvc.perform(get("/media/..%2F..%2Fetc%2Fpasswd")).andExpect(status().is4xxClientError()); // the firewall refuses it
    }

    @Test
    void anExerciseCoverShowsInTheEditorTheCatalogAndThePassage() throws Exception {
        Map<?, ?> uploaded = json.readValue(upload("cover.png", "image/png", image("png", 64, 36))
                .andReturn().getResponse().getContentAsString(), Map.class);
        long coverId = ((Number) uploaded.get("id")).longValue();
        String url = (String) uploaded.get("url");

        mvc.perform(post("/api/v1/admin/passages").header("Authorization", bearer("AUTHOR")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "with-cover", "title": "With a cover", "format": "MINI", "coverMediaId": %d, "topicIds": [1],
                                 "passageHtml": "<p>Text.</p>",
                                 "questionGroups": [{"type": "TFNG", "instruction": "True or false?",
                                   "questions": [{"text": "Text.", "answer": ["TRUE"]}]}]}""".formatted(coverId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverMediaId").value(coverId))
                .andExpect(jsonPath("$.coverUrl").value(url));
        mvc.perform(post("/api/v1/admin/passages/with-cover/publish").header("Authorization", bearer("AUTHOR"))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/exercises"))
                .andExpect(jsonPath("$.items[?(@.id == 'with-cover')].coverUrl").value(hasItem(url)))
                .andExpect(jsonPath("$.items[?(@.id == 'yellowstone-wolves')].coverUrl").value(hasItem((Object) null)));
        mvc.perform(get("/api/v1/passages/with-cover"))
                .andExpect(jsonPath("$.coverUrl").value(url))
                .andExpect(jsonPath("$.topics[0]").value("nature"));

        mvc.perform(post("/api/v1/admin/passages").header("Authorization", bearer("AUTHOR")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"bad-cover\", \"title\": \"x\", \"coverMediaId\": 999999, \"questionGroups\": []}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("coverMediaId"));
    }

    private org.springframework.test.web.servlet.ResultActions upload(String name, String type, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/v1/admin/media").file(new MockMultipartFile("file", name, type, bytes))
                .header("Authorization", bearer("AUTHOR")));
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff6b4a);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private String bearer(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("roles", List.of(role)).claim("lang", "en").build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
