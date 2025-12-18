package com.healthassistant.calories;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "calories_hourly_projections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CaloriesHourlyProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "hour", nullable = false)
    private Integer hour;

    @Column(name = "calories_kcal", nullable = false)
    @Builder.Default
    private Double caloriesKcal = 0.0;

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

    static CaloriesHourlyProjectionJpaEntity from(CaloriesBucket bucket) {
        return CaloriesHourlyProjectionJpaEntity.builder()
                .deviceId(bucket.deviceId())
                .date(bucket.date())
                .hour(bucket.hour())
                .caloriesKcal(bucket.caloriesKcal())
                .bucketCount(1)
                .firstBucketTime(bucket.bucketStart())
                .lastBucketTime(bucket.bucketEnd())
                .build();
    }

    void addBucket(CaloriesBucket bucket) {
        this.caloriesKcal = this.caloriesKcal + bucket.caloriesKcal();
        this.bucketCount = this.bucketCount + 1;

        if (this.firstBucketTime == null || bucket.bucketStart().isBefore(this.firstBucketTime)) {
            this.firstBucketTime = bucket.bucketStart();
        }
        if (this.lastBucketTime == null || bucket.bucketEnd().isAfter(this.lastBucketTime)) {
            this.lastBucketTime = bucket.bucketEnd();
        }
    }
}
