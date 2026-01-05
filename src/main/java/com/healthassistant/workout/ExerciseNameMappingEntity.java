package com.healthassistant.workout;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "exercise_name_mappings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
class ExerciseNameMappingEntity {

    @Id
    @Column(name = "exercise_name", nullable = false)
    private String exerciseName;

    @Column(name = "catalog_id", nullable = false, length = 50)
    private String catalogId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
