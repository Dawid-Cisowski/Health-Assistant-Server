package com.healthassistant.workout;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "exercises")
@Getter
@NoArgsConstructor
@AllArgsConstructor
class ExerciseDefinitionEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "primary_muscle", nullable = false)
    private String primaryMuscle;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "muscles", nullable = false, columnDefinition = "TEXT[]")
    private List<String> muscles;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "is_auto_created", nullable = false)
    private Boolean isAutoCreated = false;

    static ExerciseDefinitionEntity createAutoCreated(
            String id, String name, String description,
            String primaryMuscle, List<String> muscles) {
        return new ExerciseDefinitionEntity(
                id, name, description, primaryMuscle, muscles, Instant.now(), true
        );
    }
}
