package com.healthassistant.activity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "activity_daily_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
class ActivityDailyProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_active_minutes", nullable = false)
    @Builder.Default
    private Integer totalActiveMinutes = 0;

    @Column(name = "first_activity_time")
    private Instant firstActivityTime;

    @Column(name = "last_activity_time")
    private Instant lastActivityTime;

    @Column(name = "most_active_hour")
    private Integer mostActiveHour;

    @Column(name = "most_active_hour_minutes")
    private Integer mostActiveHourMinutes;

    @Column(name = "active_hours_count", nullable = false)
    @Builder.Default
    private Integer activeHoursCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
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

    void updateDailySummary(
        int totalActiveMinutes,
        Instant firstActivityTime,
        Instant lastActivityTime,
        int activeHoursCount,
        Integer mostActiveHour,
        Integer mostActiveHourMinutes
    ) {
        this.totalActiveMinutes = totalActiveMinutes;
        this.firstActivityTime = firstActivityTime;
        this.lastActivityTime = lastActivityTime;
        this.activeHoursCount = activeHoursCount;
        this.mostActiveHour = mostActiveHour;
        this.mostActiveHourMinutes = mostActiveHourMinutes;
    }
}
