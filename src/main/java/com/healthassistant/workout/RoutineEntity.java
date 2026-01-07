package com.healthassistant.workout;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "routines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
class RoutineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "color_theme", length = 50)
    private String colorTheme = "bg-indigo-500";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Integer version;

    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<RoutineExerciseEntity> exercises = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    void addExercise(RoutineExerciseEntity exercise) {
        exercises.add(exercise);
        exercise.setRoutine(this);
    }

    void clearExercises() {
        exercises.clear();
    }

    void updateDetails(String name, String description, String colorTheme) {
        this.name = name;
        this.description = description;
        this.colorTheme = colorTheme;
    }

    static RoutineEntity create(String deviceId, String name, String description, String colorTheme) {
        var entity = new RoutineEntity();
        entity.deviceId = deviceId;
        entity.name = name;
        entity.description = description;
        entity.colorTheme = colorTheme;
        return entity;
    }
}
