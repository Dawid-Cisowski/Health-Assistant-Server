package com.healthassistant.medicalexams;

import com.healthassistant.medicalexams.api.FileStorageService;
import com.healthassistant.medicalexams.api.dto.AttachmentStorageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.medicalexams.google-drive", name = "enabled", havingValue = "true")
class GoogleDriveStorageService implements FileStorageService {

    @Override
    public AttachmentStorageResult store(UUID examId, String examTypeCode, String filename, String contentType, byte[] data) {
        log.warn("Google Drive storage not fully implemented. File upload skipped.");
        throw new UnsupportedOperationException("Google Drive storage not configured");
    }

    @Override
    public void delete(String storageKey) {
        log.warn("Google Drive storage not fully implemented. File delete skipped.");
    }
}
