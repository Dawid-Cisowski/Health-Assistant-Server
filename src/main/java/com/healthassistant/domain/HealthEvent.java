package com.healthassistant.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a health event stored in an append-only manner.
 * The payload is stored as JSONB for flexibility.
 */
@Entity
@Table(name = "health_events", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_occurred_at", columnList = "occurred_at"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Server-generated unique event ID (e.g., evt_01HF3S3B7Z2Q9QZ8)
     */
    @Column(name = "event_id", nullable = false, unique = true, length = 32)
    private String eventId;

    /**
     * Client-provided idempotency key
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 512)
    private String idempotencyKey;

    /**
     * Event type (e.g., StepsBucketedRecorded.v1)
     */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /**
     * When the event logically occurred (client time)
     */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Event payload stored as JSONB
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /**
     * Device ID that sent this event
     */
    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    /**
     * When the event was received by the server
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

