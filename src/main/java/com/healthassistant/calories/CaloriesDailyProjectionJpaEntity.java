package com.healthassistant.calories;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "calories_daily_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
class CaloriesDailyProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_calories_kcal", nullable = false)
    @Builder.Default
    private Double totalCaloriesKcal = 0.0;

    @Column(name = "first_calorie_time")
    private Instant firstCalorieTime;

    @Column(name = "last_calorie_time")
    private Instant lastCalorieTime;

    @Column(name = "most_active_hour")
    private Integer mostActiveHour;

    @Column(name = "most_active_hour_calories")
    private Double mostActiveHourCalories;

    @Column(name = "active_hours_count", nullable = false)
    @Builder.Default
    private Integer activeHoursCount = 0;

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

    void updateFromHourlyData(List<CaloriesHourlyProjectionJpaEntity> hourlyData) {
        this.totalCaloriesKcal = hourlyData.stream()
                .mapToDouble(CaloriesHourlyProjectionJpaEntity::getCaloriesKcal)
                .sum();

        this.firstCalorieTime = hourlyData.stream()
                .map(CaloriesHourlyProjectionJpaEntity::getFirstBucketTime)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        this.lastCalorieTime = hourlyData.stream()
                .map(CaloriesHourlyProjectionJpaEntity::getLastBucketTime)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        this.activeHoursCount = (int) hourlyData.stream()
                .filter(h -> h.getCaloriesKcal() > 0)
                .count();

        hourlyData.stream()
                .max(Comparator.comparingDouble(CaloriesHourlyProjectionJpaEntity::getCaloriesKcal))
                .ifPresent(mostActive -> {
                    this.mostActiveHour = mostActive.getHour();
                    this.mostActiveHourCalories = mostActive.getCaloriesKcal();
                });
    }
}
