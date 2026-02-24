package com.healthassistant.medicalexams;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.healthassistant.medicalexams.api.FileStorageService;
import com.healthassistant.medicalexams.api.dto.AttachmentStorageResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Year;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.medicalexams.gcs", name = "enabled", havingValue = "true")
class GcsStorageService implements FileStorageService {

    @Value("${app.medicalexams.gcs.bucket-name}")
    private String bucketName;

    @Value("${app.medicalexams.gcs.credentials-file:}")
    private String credentialsFile;

    @Value("${app.medicalexams.gcs.signed-url-hours:168}")
    private int signedUrlHours;

    private Storage storage;

    @PostConstruct
    void init() {
        try {
            var builder = StorageOptions.newBuilder();
            if (credentialsFile != null && !credentialsFile.isBlank()) {
                try (var stream = new FileInputStream(credentialsFile)) {
                    builder.setCredentials(GoogleCredentials.fromStream(stream));
                }
            }
            storage = builder.build().getService();
            log.info("GCS storage initialized for bucket: {}", bucketName);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize GCS storage", e);
        }
    }

    @Override
    public AttachmentStorageResult store(UUID examId, String examTypeCode,
                                         String filename, String contentType, byte[] data) {
        var objectName = buildObjectName(examTypeCode, examId, filename);
        var blobId = BlobId.of(bucketName, objectName);
        var blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        storage.create(blobInfo, data);
        log.debug("Uploaded file to GCS bucket {}", bucketName);

        var signedUrl = generateSignedUrl(blobInfo);
        return new AttachmentStorageResult(objectName, signedUrl, null, "GCS");
    }

    @Override
    public void delete(String storageKey) {
        try {
            storage.delete(bucketName, storageKey);
            log.debug("Deleted GCS object from bucket {}", bucketName);
        } catch (Exception e) {
            log.warn("Failed to delete GCS object", e);
        }
    }

    private String buildObjectName(String examTypeCode, UUID examId, String filename) {
        var safe = filename != null
                ? filename.replaceAll("[^a-zA-Z0-9._-]", "_")
                : "file";
        return "medical-exams/%s/%d/%s/%s_%s"
                .formatted(examTypeCode, Year.now().getValue(), examId, UUID.randomUUID(), safe);
    }

    private String generateSignedUrl(BlobInfo blobInfo) {
        try {
            return storage.signUrl(blobInfo, signedUrlHours, TimeUnit.HOURS,
                    Storage.SignUrlOption.withV4Signature()).toString();
        } catch (Exception e) {
            log.warn("Could not generate signed URL (requires Service Account credentials): {}",
                    e.getMessage());
            return null;
        }
    }
}
