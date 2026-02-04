package com.healthassistant.config;

import java.time.ZoneId;
import java.util.Set;

/**
 * Shared constants for image import services.
 */
public final class ImportConstants {

    private ImportConstants() {
        throw new UnsupportedOperationException("Constants class");
    }

    /**
     * Allowed image MIME types for import.
     */
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    /**
     * Generic image types that require magic byte detection.
     */
    public static final Set<String> GENERIC_IMAGE_TYPES = Set.of(
            "image/*", "application/octet-stream"
    );

    /**
     * Maximum file size for image imports (10MB).
     */
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    /**
     * Poland timezone for date calculations.
     */
    public static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
}
