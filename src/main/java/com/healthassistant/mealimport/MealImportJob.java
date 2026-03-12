package com.healthassistant.mealimport;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meal_import_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MealImportJob {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 20)
    private MealImportJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MealImportJobStatus status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_data", columnDefinition = "jsonb")
    private List<ImageEntry> imageData;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    record ImageEntry(String base64Bytes, String contentType, String fileName) {}

    static MealImportJob createImportJob(String deviceId, String description, List<ImageEntry> imageData) {
        var job = new MealImportJob();
        job.deviceId = deviceId;
        job.jobType = MealImportJobType.IMPORT;
        job.status = MealImportJobStatus.PENDING;
        job.description = description;
        job.imageData = imageData;
        return job;
    }

    static MealImportJob createAnalyzeJob(String deviceId, String description, List<ImageEntry> imageData) {
        var job = new MealImportJob();
        job.deviceId = deviceId;
        job.jobType = MealImportJobType.ANALYZE;
        job.status = MealImportJobStatus.PENDING;
        job.description = description;
        job.imageData = imageData;
        return job;
    }

    void markProcessing() {
        this.status = MealImportJobStatus.PROCESSING;
    }

    void markDone(String resultJson) {
        this.status = MealImportJobStatus.DONE;
        this.result = resultJson;
    }

    void markFailed(String safeErrorMessage) {
        this.status = MealImportJobStatus.FAILED;
        this.errorMessage = safeErrorMessage;
    }

    boolean isPending() {
        return this.status == MealImportJobStatus.PENDING;
    }

    boolean isDone() {
        return this.status == MealImportJobStatus.DONE;
    }

    boolean isFailed() {
        return this.status == MealImportJobStatus.FAILED;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        var now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (expiresAt == null) {
            expiresAt = now.plus(2, ChronoUnit.HOURS);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
