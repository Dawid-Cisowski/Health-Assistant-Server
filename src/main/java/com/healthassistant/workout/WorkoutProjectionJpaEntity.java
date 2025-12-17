package com.healthassistant.workout;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workout_projections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class WorkoutProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workout_id", nullable = false, unique = true)
    private String workoutId;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "performed_date", nullable = false)
    private LocalDate performedDate;

    @Column(name = "source", nullable = false, length = 128)
    private String source;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "total_exercises", nullable = false)
    private Integer totalExercises;

    @Column(name = "total_sets", nullable = false)
    private Integer totalSets;

    @Column(name = "total_volume_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalVolumeKg;

    @Column(name = "total_working_volume_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalWorkingVolumeKg;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(name = "event_id", nullable = false, length = 32)
    private String eventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderInWorkout ASC")
    @Builder.Default
    private List<WorkoutExerciseProjectionJpaEntity> exercises = new ArrayList<>();

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

    public void addExercise(WorkoutExerciseProjectionJpaEntity exercise) {
        exercises.add(exercise);
        exercise.setWorkout(this);
    }

    public void removeExercise(WorkoutExerciseProjectionJpaEntity exercise) {
        exercises.remove(exercise);
        exercise.setWorkout(null);
    }
}
