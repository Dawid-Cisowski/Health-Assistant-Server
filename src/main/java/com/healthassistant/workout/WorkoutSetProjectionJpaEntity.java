package com.healthassistant.workout;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "workout_set_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
class WorkoutSetProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workout_id", nullable = false)
    private String workoutId;

    @Column(name = "exercise_name", nullable = false)
    private String exerciseName;

    @Column(name = "exercise_id", length = 50)
    private String exerciseId;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "reps", nullable = false)
    private Integer reps;

    @Column(name = "is_warmup", nullable = false)
    private Boolean isWarmup;

    @Column(name = "volume_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal volumeKg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    static WorkoutSetProjectionJpaEntity from(String workoutId, String exerciseName, String exerciseId, ExerciseSet set) {
        return WorkoutSetProjectionJpaEntity.builder()
                .workoutId(workoutId)
                .exerciseName(exerciseName)
                .exerciseId(exerciseId)
                .setNumber(set.setNumber())
                .weightKg(set.weight().kilograms())
                .reps(set.reps().count())
                .isWarmup(set.warmup())
                .volumeKg(set.volume().kilograms())
                .build();
    }
}
