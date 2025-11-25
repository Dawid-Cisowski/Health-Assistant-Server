package com.healthassistant.calories;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "calories_daily_projections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaloriesDailyProjectionJpaEntity {

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
