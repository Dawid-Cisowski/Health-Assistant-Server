package com.healthassistant.healthevents;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "health_events", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_event_type", columnList = "event_type"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_device_occurred", columnList = "device_id,occurred_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
class HealthEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "event_id", nullable = false, unique = true, length = 32)
    private String eventId;

    @Column(name = "idempotency_key", nullable = false, length = 512)
    private String idempotencyKey;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_event_id", length = 32)
    private String deletedByEventId;

    @Column(name = "superseded_by_event_id", length = 32)
    private String supersededByEventId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    void markAsDeleted(Instant deletedAt, String deletedByEventId) {
        this.deletedAt = deletedAt;
        this.deletedByEventId = deletedByEventId;
    }

    void markAsSuperseded(String supersededByEventId) {
        this.supersededByEventId = supersededByEventId;
    }
}
