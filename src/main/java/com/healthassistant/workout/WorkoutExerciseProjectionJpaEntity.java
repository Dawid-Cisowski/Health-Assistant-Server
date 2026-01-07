package com.healthassistant.workout;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "workout_exercise_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
class WorkoutExerciseProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", referencedColumnName = "workout_id", nullable = false)
    private WorkoutProjectionJpaEntity workout;

    @Column(name = "exercise_name", nullable = false)
    private String exerciseName;

    @Column(name = "exercise_id", length = 50)
    private String exerciseId;

    @Column(name = "muscle_group", length = 128)
    private String muscleGroup;

    @Column(name = "order_in_workout", nullable = false)
    private Integer orderInWorkout;

    @Column(name = "total_sets", nullable = false)
    private Integer totalSets;

    @Column(name = "total_volume_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalVolumeKg;

    @Column(name = "max_weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxWeightKg;

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

    void setWorkout(WorkoutProjectionJpaEntity workout) {
        this.workout = workout;
    }

    static WorkoutExerciseProjectionJpaEntity from(WorkoutProjectionJpaEntity workoutEntity, Exercise exercise) {
        return WorkoutExerciseProjectionJpaEntity.builder()
                .workout(workoutEntity)
                .exerciseName(exercise.name())
                .exerciseId(exercise.exerciseId())
                .muscleGroup(exercise.muscleGroup())
                .orderInWorkout(exercise.orderInWorkout())
                .totalSets(exercise.totalSets())
                .totalVolumeKg(exercise.totalVolume().kilograms())
                .maxWeightKg(exercise.maxWeight().kilograms())
                .build();
    }
}
