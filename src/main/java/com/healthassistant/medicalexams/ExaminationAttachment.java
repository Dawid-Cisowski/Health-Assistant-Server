package com.healthassistant.medicalexams;

import com.healthassistant.medicalexams.api.dto.AttachmentStorageResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "examination_attachments")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExaminationAttachment {

    @Id
    private UUID id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "storage_provider", nullable = false, length = 20)
    private String storageProvider;

    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    @Column(name = "drive_folder_id", length = 255)
    private String driveFolderId;

    @Column(name = "public_url", length = 2000)
    private String publicUrl;

    @Column(name = "attachment_type", nullable = false, length = 30)
    private String attachmentType;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static ExaminationAttachment create(Examination examination, String deviceId,
                                         String filename, String contentType, long fileSizeBytes,
                                         AttachmentStorageResult storageResult, String attachmentType,
                                         boolean isPrimary, String description) {
        var attachment = new ExaminationAttachment();
        attachment.id = UUID.randomUUID();
        attachment.examination = examination;
        attachment.deviceId = deviceId;
        attachment.filename = filename;
        attachment.contentType = contentType;
        attachment.fileSizeBytes = fileSizeBytes;
        attachment.storageProvider = storageResult.provider();
        attachment.storageKey = storageResult.storageKey();
        attachment.driveFolderId = storageResult.folderId();
        attachment.publicUrl = storageResult.publicUrl();
        attachment.attachmentType = attachmentType != null ? attachmentType : "DOCUMENT";
        attachment.primary = isPrimary;
        attachment.description = description;
        attachment.createdAt = Instant.now();
        return attachment;
    }

    boolean isPrimary() {
        return primary;
    }
}
