package com.healthassistant.workout;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "routine_exercises")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
class RoutineExerciseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private RoutineEntity routine;

    @Column(name = "exercise_id", nullable = false, length = 50)
    private String exerciseId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "default_sets")
    private Integer defaultSets = 3;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Version
    private Long version;

    void setRoutine(RoutineEntity routine) {
        this.routine = routine;
    }

    static RoutineExerciseEntity create(String exerciseId, Integer orderIndex, Integer defaultSets, String notes) {
        var entity = new RoutineExerciseEntity();
        entity.exerciseId = exerciseId;
        entity.orderIndex = orderIndex;
        entity.defaultSets = defaultSets != null ? defaultSets : 3;
        entity.notes = notes;
        return entity;
    }
}
