package com.healthassistant.steps;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "steps_daily_projections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class StepsDailyProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "total_steps", nullable = false)
    @Builder.Default
    private Integer totalSteps = 0;

    @Column(name = "first_step_time")
    private Instant firstStepTime;

    @Column(name = "last_step_time")
    private Instant lastStepTime;

    @Column(name = "most_active_hour")
    private Integer mostActiveHour;

    @Column(name = "most_active_hour_steps")
    private Integer mostActiveHourSteps;

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
