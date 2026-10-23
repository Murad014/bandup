package com.bandup.api.content.application.port;

import java.util.Optional;
import java.util.UUID;

/** Port: the {@code media} rows (metadata of stored files). */
public interface MediaLibrary {

    /** Records a stored image; returns its internal id and public id. */
    MediaRef addImage(ImageStore.StoredImage image);

    /** The stored image with this public id, if there is one. */
    Optional<MediaFile> findImage(UUID publicId);

    Optional<UUID> publicIdOf(long mediaId);

    boolean isImage(long mediaId);

    record MediaRef(long id, UUID publicId) {
    }

    record MediaFile(String storageKey, String mimeType) {
    }
}
