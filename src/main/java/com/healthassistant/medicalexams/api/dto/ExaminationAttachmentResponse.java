package com.healthassistant.medicalexams.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ExaminationAttachmentResponse(
        UUID id,
        String filename,
        String contentType,
        long fileSizeBytes,
        String storageProvider,
        String publicUrl,
        String attachmentType,
        boolean isPrimary,
        String description,
        Instant createdAt
) {
}
