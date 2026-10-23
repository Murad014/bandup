package com.bandup.api.content.application.port;

import java.util.Optional;

/** Port: where image files live (a folder now; cloud storage later). */
public interface ImageStore {

    /**
     * Checks that {@code bytes} really are a JPEG or PNG image and stores them.
     *
     * @return empty if the bytes aren't a readable JPEG or PNG
     */
    Optional<StoredImage> store(byte[] bytes);

    Optional<byte[]> read(String storageKey);

    /** A stored file: its key, real type and size in pixels. */
    record StoredImage(String storageKey, String mimeType, long byteSize, int width, int height) {
    }
}
