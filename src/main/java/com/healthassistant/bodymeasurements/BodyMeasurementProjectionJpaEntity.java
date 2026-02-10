package com.healthassistant.bodymeasurements;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "body_measurement_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
class BodyMeasurementProjectionJpaEntity {

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

    // Arms
    @Column(name = "biceps_left_cm", precision = 5, scale = 2)
    private BigDecimal bicepsLeftCm;

    @Column(name = "biceps_right_cm", precision = 5, scale = 2)
    private BigDecimal bicepsRightCm;

    @Column(name = "forearm_left_cm", precision = 5, scale = 2)
    private BigDecimal forearmLeftCm;

    @Column(name = "forearm_right_cm", precision = 5, scale = 2)
    private BigDecimal forearmRightCm;

    // Torso
    @Column(name = "chest_cm", precision = 5, scale = 2)
    private BigDecimal chestCm;

    @Column(name = "waist_cm", precision = 5, scale = 2)
    private BigDecimal waistCm;

    @Column(name = "abdomen_cm", precision = 5, scale = 2)
    private BigDecimal abdomenCm;

    @Column(name = "hips_cm", precision = 5, scale = 2)
    private BigDecimal hipsCm;

    @Column(name = "neck_cm", precision = 5, scale = 2)
    private BigDecimal neckCm;

    @Column(name = "shoulders_cm", precision = 5, scale = 2)
    private BigDecimal shouldersCm;

    // Legs
    @Column(name = "thigh_left_cm", precision = 5, scale = 2)
    private BigDecimal thighLeftCm;

    @Column(name = "thigh_right_cm", precision = 5, scale = 2)
    private BigDecimal thighRightCm;

    @Column(name = "calf_left_cm", precision = 5, scale = 2)
    private BigDecimal calfLeftCm;

    @Column(name = "calf_right_cm", precision = 5, scale = 2)
    private BigDecimal calfRightCm;

    @Column(name = "notes", length = 500)
    private String notes;

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

    static BodyMeasurementProjectionJpaEntity from(BodyMeasurement measurement) {
        return BodyMeasurementProjectionJpaEntity.builder()
                .deviceId(measurement.deviceId())
                .eventId(measurement.eventId())
                .measurementId(measurement.measurementId())
                .date(measurement.date())
                .measuredAt(measurement.measuredAt())
                .bicepsLeftCm(measurement.bicepsLeftCm())
                .bicepsRightCm(measurement.bicepsRightCm())
                .forearmLeftCm(measurement.forearmLeftCm())
                .forearmRightCm(measurement.forearmRightCm())
                .chestCm(measurement.chestCm())
                .waistCm(measurement.waistCm())
                .abdomenCm(measurement.abdomenCm())
                .hipsCm(measurement.hipsCm())
                .neckCm(measurement.neckCm())
                .shouldersCm(measurement.shouldersCm())
                .thighLeftCm(measurement.thighLeftCm())
                .thighRightCm(measurement.thighRightCm())
                .calfLeftCm(measurement.calfLeftCm())
                .calfRightCm(measurement.calfRightCm())
                .notes(measurement.notes())
                .build();
    }

    void applyMeasurementCorrection(BodyMeasurement correctedMeasurement) {
        if (correctedMeasurement == null) {
            throw new IllegalArgumentException("Corrected measurement cannot be null");
        }
        this.measurementId = correctedMeasurement.measurementId();
        this.date = correctedMeasurement.date();
        this.measuredAt = correctedMeasurement.measuredAt();
        this.bicepsLeftCm = correctedMeasurement.bicepsLeftCm();
        this.bicepsRightCm = correctedMeasurement.bicepsRightCm();
        this.forearmLeftCm = correctedMeasurement.forearmLeftCm();
        this.forearmRightCm = correctedMeasurement.forearmRightCm();
        this.chestCm = correctedMeasurement.chestCm();
        this.waistCm = correctedMeasurement.waistCm();
        this.abdomenCm = correctedMeasurement.abdomenCm();
        this.hipsCm = correctedMeasurement.hipsCm();
        this.neckCm = correctedMeasurement.neckCm();
        this.shouldersCm = correctedMeasurement.shouldersCm();
        this.thighLeftCm = correctedMeasurement.thighLeftCm();
        this.thighRightCm = correctedMeasurement.thighRightCm();
        this.calfLeftCm = correctedMeasurement.calfLeftCm();
        this.calfRightCm = correctedMeasurement.calfRightCm();
        this.notes = correctedMeasurement.notes();
    }
}
