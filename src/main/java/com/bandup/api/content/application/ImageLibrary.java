package com.bandup.api.content.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.content.application.port.ImageStore;
import com.bandup.api.content.application.port.MediaLibrary;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * Cover images: upload (authors) and download (everyone). Only real JPEG or
 * PNG images up to 3 MB are accepted — the bytes are decoded, the file name
 * and declared type aren't trusted.
 */
@Service
@RequiredArgsConstructor
public class ImageLibrary {

    public static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final Set<String> DECLARED = Set.of("image/jpeg", "image/png");

    private final ImageStore store;
    private final MediaLibrary media;

    /** An uploaded image: {@code id} goes into the exercise ({@code coverMediaId}), {@code url} shows it. */
    public record UploadedImage(long id, String url, int width, int height) {
    }

    /** A stored image's bytes and type. */
    public record ImageFile(byte[] bytes, String mimeType) {
    }

    /** @throws DomainException ({@code file}) when it isn't a JPEG / PNG up to 3 MB */
    @Transactional
    public UploadedImage upload(byte[] bytes, String declaredType) {
        if (bytes == null || bytes.length == 0) {
            throw DomainException.of("file", "Choose an image to upload.");
        }
        if (bytes.length > MAX_BYTES) {
            throw DomainException.of("file", "The image is too large (at most 3 MB).");
        }
        if (declaredType == null || !DECLARED.contains(declaredType)) {
            throw DomainException.of("file", "Use a JPEG or PNG image.");
        }
        ImageStore.StoredImage stored = store.store(bytes)
                .orElseThrow(() -> DomainException.of("file", "This file isn't a readable JPEG or PNG image."));
        MediaLibrary.MediaRef ref = media.addImage(stored);
        return new UploadedImage(ref.id(), urlOf(ref.publicId()), stored.width(), stored.height());
    }

    @Transactional(readOnly = true)
    public Optional<ImageFile> download(UUID publicId) {
        return media.findImage(publicId)
                .flatMap(file -> store.read(file.storageKey()).map(bytes -> new ImageFile(bytes, file.mimeType())));
    }

    /** The public address of an image by its internal id, if it exists. */
    @Transactional(readOnly = true)
    public Optional<String> urlOf(long mediaId) {
        return media.publicIdOf(mediaId).map(ImageLibrary::urlOf);
    }

    static String urlOf(UUID publicId) {
        return "/media/" + publicId;
    }
}
