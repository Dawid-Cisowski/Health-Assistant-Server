package com.healthassistant.heartrate;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "heart_rate_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class HeartRateProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "avg_bpm", nullable = false)
    private Integer avgBpm;

    @Column(name = "min_bpm")
    private Integer minBpm;

    @Column(name = "max_bpm")
    private Integer maxBpm;

    @Column(name = "samples", nullable = false)
    private Integer samples;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    static HeartRateProjectionJpaEntity create(
            String deviceId,
            String eventId,
            Instant measuredAt,
            Integer avgBpm,
            Integer minBpm,
            Integer maxBpm,
            Integer samples
    ) {
        HeartRateProjectionJpaEntity entity = new HeartRateProjectionJpaEntity();
        entity.deviceId = deviceId;
        entity.eventId = eventId;
        entity.measuredAt = measuredAt;
        entity.avgBpm = avgBpm;
        entity.minBpm = minBpm;
        entity.maxBpm = maxBpm;
        entity.samples = samples;
        entity.createdAt = Instant.now();
        return entity;
    }

    void updateMeasurement(Integer avgBpm, Integer minBpm, Integer maxBpm, Integer samples) {
        this.avgBpm = avgBpm;
        this.minBpm = minBpm;
        this.maxBpm = maxBpm;
        this.samples = samples;
    }
}
