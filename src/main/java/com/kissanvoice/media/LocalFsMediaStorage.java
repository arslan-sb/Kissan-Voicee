package com.kissanvoice.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Placeholder adapter so Block 2 has a working upload path before S3 exists.
 * Replaced in Block 3 by S3MediaStorage; this class stays as the offline
 * fallback for tests that should not need LocalStack.
 */
@Component
@ConditionalOnProperty(name = "kissanvoice.media.provider", havingValue = "local", matchIfMissing = true)
public class LocalFsMediaStorage implements MediaStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalFsMediaStorage.class);
    private final Path root;

    public LocalFsMediaStorage(@Value("${kissanvoice.media.local-root:./.media}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String store(String key, InputStream content, long sizeBytes, String contentType)
            throws IOException {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to write outside the media root: " + key);
        }
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Stored {} bytes at {}", sizeBytes, target);
        return key;
    }

    @Override
    public String presignedUrl(String key) {
        return root.resolve(key).toUri().toString();
    }
}
