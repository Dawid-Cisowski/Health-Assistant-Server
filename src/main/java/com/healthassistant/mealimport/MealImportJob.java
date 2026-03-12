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

    static MealImportJob create(MealImportJobType jobType, String deviceId, String description, List<ImageEntry> imageData) {
        var now = Instant.now();
        var job = new MealImportJob();
        job.id = UUID.randomUUID();
        job.deviceId = deviceId;
        job.jobType = jobType;
        job.status = MealImportJobStatus.PENDING;
        job.description = description;
        job.imageData = imageData;
        job.createdAt = now;
        job.updatedAt = now;
        job.expiresAt = now.plus(2, ChronoUnit.HOURS);
        return job;
    }

    void markProcessing() {
        if (this.status != MealImportJobStatus.PENDING) {
            throw new IllegalStateException("Cannot transition to PROCESSING from " + this.status);
        }
        this.status = MealImportJobStatus.PROCESSING;
    }

    void markDone(String resultJson) {
        if (this.status != MealImportJobStatus.PROCESSING) {
            throw new IllegalStateException("Cannot transition to DONE from " + this.status);
        }
        this.status = MealImportJobStatus.DONE;
        this.result = resultJson;
        this.imageData = null;
    }

    void markFailed(String safeErrorMessage) {
        this.status = MealImportJobStatus.FAILED;
        this.errorMessage = safeErrorMessage;
        this.imageData = null;
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

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
