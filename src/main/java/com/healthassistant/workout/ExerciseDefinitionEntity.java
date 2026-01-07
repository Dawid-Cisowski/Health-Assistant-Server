package com.healthassistant.workout;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "exercises")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    private Boolean isAutoCreated;

    @Version
    private Long version;

    private ExerciseDefinitionEntity(
            String id, String name, String description,
            String primaryMuscle, List<String> muscles,
            Instant createdAt, Boolean isAutoCreated) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.primaryMuscle = primaryMuscle;
        this.muscles = muscles;
        this.createdAt = createdAt;
        this.isAutoCreated = isAutoCreated;
    }

    static ExerciseDefinitionEntity createAutoCreated(
            String id, String name, String description,
            String primaryMuscle, List<String> muscles) {
        return new ExerciseDefinitionEntity(
                id, name, description, primaryMuscle, muscles, Instant.now(), true
        );
    }
}
