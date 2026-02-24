package com.healthassistant.medicalexams.api;

import com.healthassistant.medicalexams.api.dto.AttachmentStorageResult;

import java.util.UUID;

public interface FileStorageService {

    AttachmentStorageResult store(UUID examId, String examTypeCode, String filename, String contentType, byte[] data);

    void delete(String storageKey);
}
