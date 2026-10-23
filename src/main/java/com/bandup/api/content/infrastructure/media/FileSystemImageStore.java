package com.bandup.api.content.infrastructure.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bandup.api.content.application.port.ImageStore;

/**
 * {@link ImageStore} in a folder ({@code bandup.media.dir}, default
 * {@code data/media}). The bytes are decoded with ImageIO: only real JPEG and
 * PNG images are kept, under a random name — never the uploaded one.
 */
@Component
class FileSystemImageStore implements ImageStore {

    private static final Pattern KEY = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png)");

    private final Path dir;

    FileSystemImageStore(@Value("${bandup.media.dir:data/media}") String dir) {
        this.dir = Path.of(dir);
    }

    @Override
    public Optional<StoredImage> store(byte[] bytes) {
        Optional<String> format = formatOf(bytes);
        if (format.isEmpty()) {
            return Optional.empty();
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
            return Optional.empty();
        }
        boolean png = format.get().equals("png");
        String key = UUID.randomUUID() + (png ? ".png" : ".jpg");
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(key), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't store the image", e);
        }
        return Optional.of(new StoredImage(key, png ? "image/png" : "image/jpeg", bytes.length, image.getWidth(), image.getHeight()));
    }

    @Override
    public Optional<byte[]> read(String storageKey) {
        if (!KEY.matcher(storageKey).matches()) {
            return Optional.empty(); // only names this store created: no path tricks
        }
        Path file = dir.resolve(storageKey);
        try {
            return Files.exists(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read the image", e);
        }
    }

    /** "jpeg" / "png" from the bytes themselves (ImageIO's reader), else empty. */
    private static Optional<String> formatOf(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            String name = readers.next().getFormatName().toLowerCase(Locale.ROOT);
            return name.equals("jpeg") || name.equals("png") ? Optional.of(name) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
