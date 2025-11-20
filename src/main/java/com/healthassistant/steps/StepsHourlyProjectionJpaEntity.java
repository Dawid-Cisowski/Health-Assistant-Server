package com.healthassistant.steps;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "steps_hourly_projections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class StepsHourlyProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "hour", nullable = false)
    private Integer hour;

    @Column(name = "step_count", nullable = false)
    @Builder.Default
    private Integer stepCount = 0;

    @Column(name = "bucket_count", nullable = false)
    @Builder.Default
    private Integer bucketCount = 0;

    @Column(name = "first_bucket_time")
    private Instant firstBucketTime;

    @Column(name = "last_bucket_time")
    private Instant lastBucketTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
