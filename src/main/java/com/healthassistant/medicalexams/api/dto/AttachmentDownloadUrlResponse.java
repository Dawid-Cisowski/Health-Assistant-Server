package com.healthassistant.medicalexams.api.dto;

public record AttachmentDownloadUrlResponse(
        String url,
        String storageProvider,
        int expiresInSeconds
) {
}
