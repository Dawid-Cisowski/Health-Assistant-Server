package com.healthassistant.googlefit;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "historical_sync_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class HistoricalSyncTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_date", nullable = false)
    private LocalDate syncDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SyncTaskStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "events_synced")
    private Integer eventsSynced;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    enum SyncTaskStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }

    HistoricalSyncTask(LocalDate syncDate) {
        this.syncDate = syncDate;
        this.status = SyncTaskStatus.PENDING;
        this.retryCount = 0;
    }

    void markInProgress() {
        this.status = SyncTaskStatus.IN_PROGRESS;
    }

    void markCompleted(int eventsSynced) {
        this.status = SyncTaskStatus.COMPLETED;
        this.eventsSynced = eventsSynced;
        clearErrorMessage();
    }

    private void clearErrorMessage() {
        this.errorMessage = "";
    }

    void markFailed(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;
        if (this.retryCount >= 3) {
            this.status = SyncTaskStatus.FAILED;
        } else {
            this.status = SyncTaskStatus.PENDING;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
