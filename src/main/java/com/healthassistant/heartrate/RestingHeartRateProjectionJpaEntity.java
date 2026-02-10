package com.healthassistant.heartrate;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "resting_heart_rate_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RestingHeartRateProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "resting_bpm", nullable = false)
    private Integer restingBpm;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    static RestingHeartRateProjectionJpaEntity create(
            String deviceId,
            String eventId,
            LocalDate date,
            Integer restingBpm,
            Instant measuredAt
    ) {
        RestingHeartRateProjectionJpaEntity entity = new RestingHeartRateProjectionJpaEntity();
        entity.deviceId = deviceId;
        entity.eventId = eventId;
        entity.date = date;
        entity.restingBpm = restingBpm;
        entity.measuredAt = measuredAt;
        entity.createdAt = Instant.now();
        return entity;
    }

    void updateRestingBpm(Integer restingBpm, Instant measuredAt) {
        this.restingBpm = restingBpm;
        this.measuredAt = measuredAt;
    }

    void updateEventId(String eventId) {
        this.eventId = eventId;
    }
}
