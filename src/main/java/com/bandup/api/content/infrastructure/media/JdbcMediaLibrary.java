package com.bandup.api.content.infrastructure.media;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.content.application.port.ImageStore;
import com.bandup.api.content.application.port.MediaLibrary;

/** {@link MediaLibrary} on the {@code media} table. */
@Component
@RequiredArgsConstructor
class JdbcMediaLibrary implements MediaLibrary {

    private final JdbcClient jdbc;

    @Override
    public MediaRef addImage(ImageStore.StoredImage image) {
        return jdbc.sql("""
                insert into media (kind, storage_key, mime_type, byte_size, width, height)
                values ('IMAGE', ?, ?, ?, ?, ?) returning id, public_id""")
                .params(image.storageKey(), image.mimeType(), image.byteSize(), image.width(), image.height())
                .query((rs, i) -> new MediaRef(rs.getLong("id"), rs.getObject("public_id", UUID.class)))
                .single();
    }

    @Override
    public Optional<MediaFile> findImage(UUID publicId) {
        return jdbc.sql("select storage_key, mime_type from media where public_id = ? and kind = 'IMAGE'")
                .param(publicId)
                .query((rs, i) -> new MediaFile(rs.getString("storage_key"), rs.getString("mime_type")))
                .optional();
    }

    @Override
    public Optional<UUID> publicIdOf(long mediaId) {
        return jdbc.sql("select public_id from media where id = ?").param(mediaId).query(UUID.class).optional();
    }

    @Override
    public boolean isImage(long mediaId) {
        return jdbc.sql("select count(*) from media where id = ? and kind = 'IMAGE'").param(mediaId).query(Long.class).single() > 0;
    }
}
