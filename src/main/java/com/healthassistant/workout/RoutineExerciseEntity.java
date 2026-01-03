package com.healthassistant.workout;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "routine_exercises")
@Getter
@Setter
@NoArgsConstructor
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
}
