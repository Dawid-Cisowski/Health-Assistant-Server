package com.healthassistant.weight;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "weight_measurement_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
class WeightMeasurementProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "measurement_id", nullable = false)
    private String measurementId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "score")
    private Integer score;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "bmi", precision = 5, scale = 2)
    private BigDecimal bmi;

    @Column(name = "body_fat_percent", precision = 5, scale = 2)
    private BigDecimal bodyFatPercent;

    @Column(name = "muscle_percent", precision = 5, scale = 2)
    private BigDecimal musclePercent;

    @Column(name = "hydration_percent", precision = 5, scale = 2)
    private BigDecimal hydrationPercent;

    @Column(name = "bone_mass_kg", precision = 5, scale = 2)
    private BigDecimal boneMassKg;

    @Column(name = "bmr_kcal")
    private Integer bmrKcal;

    @Column(name = "visceral_fat_level")
    private Integer visceralFatLevel;

    @Column(name = "subcutaneous_fat_percent", precision = 5, scale = 2)
    private BigDecimal subcutaneousFatPercent;

    @Column(name = "protein_percent", precision = 5, scale = 2)
    private BigDecimal proteinPercent;

    @Column(name = "metabolic_age")
    private Integer metabolicAge;

    @Column(name = "ideal_weight_kg", precision = 6, scale = 2)
    private BigDecimal idealWeightKg;

    @Column(name = "weight_control_kg", precision = 6, scale = 2)
    private BigDecimal weightControlKg;

    @Column(name = "fat_mass_kg", precision = 6, scale = 2)
    private BigDecimal fatMassKg;

    @Column(name = "lean_body_mass_kg", precision = 6, scale = 2)
    private BigDecimal leanBodyMassKg;

    @Column(name = "muscle_mass_kg", precision = 6, scale = 2)
    private BigDecimal muscleMassKg;

    @Column(name = "protein_mass_kg", precision = 6, scale = 2)
    private BigDecimal proteinMassKg;

    @Column(name = "body_type", length = 50)
    private String bodyType;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

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

    static WeightMeasurementProjectionJpaEntity from(WeightMeasurement measurement) {
        return WeightMeasurementProjectionJpaEntity.builder()
                .deviceId(measurement.deviceId())
                .eventId(measurement.eventId())
                .measurementId(measurement.measurementId())
                .date(measurement.date())
                .measuredAt(measurement.measuredAt())
                .score(measurement.score())
                .weightKg(measurement.weightKg())
                .bmi(measurement.bmi())
                .bodyFatPercent(measurement.bodyFatPercent())
                .musclePercent(measurement.musclePercent())
                .hydrationPercent(measurement.hydrationPercent())
                .boneMassKg(measurement.boneMassKg())
                .bmrKcal(measurement.bmrKcal())
                .visceralFatLevel(measurement.visceralFatLevel())
                .subcutaneousFatPercent(measurement.subcutaneousFatPercent())
                .proteinPercent(measurement.proteinPercent())
                .metabolicAge(measurement.metabolicAge())
                .idealWeightKg(measurement.idealWeightKg())
                .weightControlKg(measurement.weightControlKg())
                .fatMassKg(measurement.fatMassKg())
                .leanBodyMassKg(measurement.leanBodyMassKg())
                .muscleMassKg(measurement.muscleMassKg())
                .proteinMassKg(measurement.proteinMassKg())
                .bodyType(measurement.bodyType())
                .source(measurement.source())
                .build();
    }

    /**
     * Updates this projection with corrected measurement data from a re-submitted event.
     * Used when idempotent event ingestion receives updated payload for same measurement.
     *
     * @param correctedMeasurement the corrected measurement data (must not be null)
     * @throws IllegalArgumentException if correctedMeasurement is null
     */
    void applyMeasurementCorrection(WeightMeasurement correctedMeasurement) {
        if (correctedMeasurement == null) {
            throw new IllegalArgumentException("Corrected measurement cannot be null");
        }
        this.measuredAt = correctedMeasurement.measuredAt();
        this.score = correctedMeasurement.score();
        this.weightKg = correctedMeasurement.weightKg();
        this.bmi = correctedMeasurement.bmi();
        this.bodyFatPercent = correctedMeasurement.bodyFatPercent();
        this.musclePercent = correctedMeasurement.musclePercent();
        this.hydrationPercent = correctedMeasurement.hydrationPercent();
        this.boneMassKg = correctedMeasurement.boneMassKg();
        this.bmrKcal = correctedMeasurement.bmrKcal();
        this.visceralFatLevel = correctedMeasurement.visceralFatLevel();
        this.subcutaneousFatPercent = correctedMeasurement.subcutaneousFatPercent();
        this.proteinPercent = correctedMeasurement.proteinPercent();
        this.metabolicAge = correctedMeasurement.metabolicAge();
        this.idealWeightKg = correctedMeasurement.idealWeightKg();
        this.weightControlKg = correctedMeasurement.weightControlKg();
        this.fatMassKg = correctedMeasurement.fatMassKg();
        this.leanBodyMassKg = correctedMeasurement.leanBodyMassKg();
        this.muscleMassKg = correctedMeasurement.muscleMassKg();
        this.proteinMassKg = correctedMeasurement.proteinMassKg();
        this.bodyType = correctedMeasurement.bodyType();
        this.source = correctedMeasurement.source();
    }
}
