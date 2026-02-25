package com.healthassistant.medicalexamimport;

import com.healthassistant.medicalexamimport.api.dto.ExtractedResultData;
import com.healthassistant.medicalexamimport.api.dto.MedicalExamDraftUpdateRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "medical_exam_import_drafts", indexes = {
        @Index(name = "idx_medical_exam_import_drafts_device", columnList = "device_id"),
        @Index(name = "idx_medical_exam_import_drafts_cleanup", columnList = "status, expires_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MedicalExamImportDraft {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", nullable = false, columnDefinition = "jsonb")
    private ExtractedData extractedData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "original_filenames", columnDefinition = "jsonb")
    private List<String> originalFilenames;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stored_files", columnDefinition = "jsonb")
    private List<StoredFile> storedFiles;

    @Column(name = "ai_confidence", precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    @Column(name = "prompt_tokens")
    private Long promptTokens;

    @Column(name = "completion_tokens")
    private Long completionTokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DraftStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    enum DraftStatus {
        PENDING, CONFIRMED, EXPIRED
    }

    /**
     * Metadata for a file already uploaded to storage during the analyze step.
     * Allows attaching the original document to the examination at confirm time
     * without re-uploading.
     */
    record StoredFile(
            String storageKey,
            String publicUrl,
            String provider,
            String filename,
            String contentType,
            long fileSize
    ) {}

    /**
     * Embedded record stored as JSONB — holds all extracted exam data that can be
     * user-modified before confirmation.
     */
    record ExtractedData(
            String examTypeCode,
            String title,
            String date,
            String performedAt,
            String laboratory,
            String orderingDoctor,
            String reportText,
            String conclusions,
            List<ExtractedResultData> results
    ) {}

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = DraftStatus.PENDING;
        if (expiresAt == null) expiresAt = now.plus(24, ChronoUnit.HOURS);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    void attachStoredFiles(List<StoredFile> files) {
        this.storedFiles = files;
        this.updatedAt = Instant.now();
    }

    static MedicalExamImportDraft create(String deviceId, ExtractedExamData extraction,
                                         List<String> originalFilenames) {
        var draft = new MedicalExamImportDraft();
        draft.deviceId = deviceId;
        draft.originalFilenames = originalFilenames;
        draft.aiConfidence = extraction.confidence();
        draft.promptTokens = extraction.promptTokens();
        draft.completionTokens = extraction.completionTokens();
        draft.extractedData = new ExtractedData(
                extraction.examTypeCode(),
                extraction.title(),
                extraction.date() != null ? extraction.date().toString() : null,
                extraction.performedAt() != null ? extraction.performedAt().toString() : null,
                extraction.laboratory(),
                extraction.orderingDoctor(),
                extraction.reportText(),
                extraction.conclusions(),
                extraction.results()
        );
        return draft;
    }

    void applyUpdate(MedicalExamDraftUpdateRequest request) {
        var current = this.extractedData;
        this.extractedData = new ExtractedData(
                request.examTypeCode() != null ? request.examTypeCode() : current.examTypeCode(),
                request.title() != null ? request.title() : current.title(),
                request.date() != null ? request.date().toString() : current.date(),
                request.performedAt() != null ? request.performedAt().toString() : current.performedAt(),
                request.laboratory() != null ? request.laboratory() : current.laboratory(),
                request.orderingDoctor() != null ? request.orderingDoctor() : current.orderingDoctor(),
                request.reportText() != null ? request.reportText() : current.reportText(),
                request.conclusions() != null ? request.conclusions() : current.conclusions(),
                request.results() != null ? request.results() : current.results()
        );
        this.updatedAt = Instant.now();
    }

    void markConfirmed() {
        this.status = DraftStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    boolean isPending() {
        return this.status == DraftStatus.PENDING;
    }

    boolean isExpired() {
        return this.expiresAt.isBefore(Instant.now());
    }
}
