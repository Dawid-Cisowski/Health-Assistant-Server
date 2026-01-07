package com.healthassistant.sleep;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "sleep_daily_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
class SleepDailyProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_sleep_minutes", nullable = false)
    @Builder.Default
    private Integer totalSleepMinutes = 0;

    @Column(name = "sleep_count", nullable = false)
    @Builder.Default
    private Integer sleepCount = 0;

    @Column(name = "first_sleep_start")
    private Instant firstSleepStart;

    @Column(name = "last_sleep_end")
    private Instant lastSleepEnd;

    @Column(name = "longest_session_minutes")
    private Integer longestSessionMinutes;

    @Column(name = "shortest_session_minutes")
    private Integer shortestSessionMinutes;

    @Column(name = "average_session_minutes")
    private Integer averageSessionMinutes;

    @Column(name = "total_light_sleep_minutes")
    @Builder.Default
    private Integer totalLightSleepMinutes = 0;

    @Column(name = "total_deep_sleep_minutes")
    @Builder.Default
    private Integer totalDeepSleepMinutes = 0;

    @Column(name = "total_rem_sleep_minutes")
    @Builder.Default
    private Integer totalRemSleepMinutes = 0;

    @Column(name = "total_awake_minutes")
    @Builder.Default
    private Integer totalAwakeMinutes = 0;

    @Column(name = "average_sleep_score")
    private Integer averageSleepScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

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

    void updateDailyStats(int totalSleepMinutes,
                          int sleepCount,
                          Instant firstSleepStart,
                          Instant lastSleepEnd,
                          Integer longestSessionMinutes,
                          Integer shortestSessionMinutes,
                          int averageSessionMinutes,
                          int totalLightSleepMinutes,
                          int totalDeepSleepMinutes,
                          int totalRemSleepMinutes,
                          int totalAwakeMinutes,
                          Integer averageSleepScore) {
        this.totalSleepMinutes = totalSleepMinutes;
        this.sleepCount = sleepCount;
        this.firstSleepStart = firstSleepStart;
        this.lastSleepEnd = lastSleepEnd;
        this.longestSessionMinutes = longestSessionMinutes;
        this.shortestSessionMinutes = shortestSessionMinutes;
        this.averageSessionMinutes = averageSessionMinutes;
        this.totalLightSleepMinutes = totalLightSleepMinutes;
        this.totalDeepSleepMinutes = totalDeepSleepMinutes;
        this.totalRemSleepMinutes = totalRemSleepMinutes;
        this.totalAwakeMinutes = totalAwakeMinutes;
        this.averageSleepScore = averageSleepScore;
    }
}
