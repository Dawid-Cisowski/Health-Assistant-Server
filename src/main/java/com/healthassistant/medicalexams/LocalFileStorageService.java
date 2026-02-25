package com.healthassistant.medicalexams;

import com.healthassistant.medicalexams.api.FileStorageService;
import com.healthassistant.medicalexams.api.dto.AttachmentStorageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.medicalexams.gcs", name = "enabled", havingValue = "false", matchIfMissing = true)
class LocalFileStorageService implements FileStorageService {

    @Value("${app.medicalexams.storage.local-path:${java.io.tmpdir}/healthassistant/medical-exams}")
    private String localBasePath;

    @Override
    public AttachmentStorageResult store(UUID examId, String examTypeCode, String filename, String contentType, byte[] data) {
        try {
            var dir = Path.of(localBasePath, examTypeCode, examId.toString());
            Files.createDirectories(dir);
            var safeFilename = UUID.randomUUID() + "_" + sanitizeFilename(filename);
            var target = dir.resolve(safeFilename);
            Files.write(target, data);
            var storageKey = target.toString();
            log.debug("Stored file locally at: {}", storageKey);
            return new AttachmentStorageResult(storageKey, null, null, "LOCAL");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file locally: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            var path = Path.of(storageKey);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete local file: {}", storageKey, e);
        }
    }

    @Override
    public String generateDownloadUrl(String storageKey) {
        return null; // Local files have no public URL
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
