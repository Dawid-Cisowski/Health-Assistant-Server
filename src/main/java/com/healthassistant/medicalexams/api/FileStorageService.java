package com.healthassistant.medicalexams.api;

import com.healthassistant.medicalexams.api.dto.AttachmentStorageResult;

import java.util.UUID;

public interface FileStorageService {

    AttachmentStorageResult store(UUID examId, String examTypeCode, String filename, String contentType, byte[] data);

    void delete(String storageKey);

    /**
     * Generates a fresh time-limited download URL for an already-stored file.
     * Returns null if the storage provider does not support public URLs (e.g. LOCAL).
     */
    String generateDownloadUrl(String storageKey);
}
