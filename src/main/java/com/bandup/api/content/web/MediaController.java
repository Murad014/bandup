package com.bandup.api.content.web;

import java.time.Duration;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.bandup.api.content.application.ImageLibrary;

/**
 * {@code GET /media/{publicId}}: a stored image, for everyone (covers are
 * public). A file never changes once stored, so browsers may keep it a year.
 */
@RestController
@RequiredArgsConstructor
class MediaController {

    private final ImageLibrary images;

    @GetMapping("/media/{publicId}")
    ResponseEntity<byte[]> image(@PathVariable String publicId) {
        UUID id;
        try {
            id = UUID.fromString(publicId);
        } catch (IllegalArgumentException notAnId) {
            return ResponseEntity.notFound().build();
        }
        return images.download(id)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.mimeType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(file.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
