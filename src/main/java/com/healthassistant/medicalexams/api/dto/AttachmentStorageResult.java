package com.healthassistant.medicalexams.api.dto;

public record AttachmentStorageResult(
        String storageKey,
        String publicUrl,
        String folderId,
        String provider
) {
}
