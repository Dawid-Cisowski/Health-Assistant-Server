package com.healthassistant.medicalexams;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

class FileValidationUtils {

    private static final long MAX_FILE_SIZE = 15 * 1024 * 1024L;
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf"
    );

    static void validate(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File too large. Maximum size is 15MB.");
        }
        var contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
        var filename = file.getOriginalFilename();
        if (filename != null && (filename.contains("..") || filename.contains("/") || filename.contains("\\"))) {
            throw new IllegalArgumentException("Invalid filename.");
        }
    }

    private FileValidationUtils() {
    }
}
