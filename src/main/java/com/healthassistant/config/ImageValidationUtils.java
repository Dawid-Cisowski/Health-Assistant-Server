package com.healthassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Shared image validation utilities for import services.
 */
public final class ImageValidationUtils {

    private static final Logger log = LoggerFactory.getLogger(ImageValidationUtils.class);

    private ImageValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Detects image MIME type from magic bytes.
     *
     * @param image the image file to detect
     * @return detected MIME type or null if unknown
     */
    public static String detectImageType(MultipartFile image) {
        try (InputStream is = image.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                return null;
            }

            // JPEG: FF D8 FF
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return "image/jpeg";
            }

            // PNG: 89 50 4E 47
            if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return "image/png";
            }

            // WebP: RIFF....WEBP
            if (read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                    && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
                return "image/webp";
            }

            return null;
        } catch (IOException e) {
            log.warn("Failed to detect image type from magic bytes", e);
            return null;
        }
    }

    /**
     * Validates a single image file for import.
     *
     * @param image the image to validate
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateImage(MultipartFile image) {
        if (image.isEmpty()) {
            throw new IllegalArgumentException("Image file is empty");
        }

        if (image.getSize() > ImportConstants.MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Image file exceeds maximum size of 10MB");
        }

        String contentType = image.getContentType();
        if (contentType != null && ImportConstants.ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return;
        }

        if (contentType != null && ImportConstants.GENERIC_IMAGE_TYPES.contains(contentType)) {
            String detectedType = detectImageType(image);
            if (detectedType != null && ImportConstants.ALLOWED_CONTENT_TYPES.contains(detectedType)) {
                log.debug("Detected image type {} from magic bytes (client sent {})", detectedType, contentType);
                return;
            }
        }

        throw new IllegalArgumentException(
                "Invalid image type '" + SecurityUtils.sanitizeForLog(contentType) + "'. Allowed: JPEG, PNG, WebP"
        );
    }
}
