package com.healthassistant.weight;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

record WeightMeasurement(
    String deviceId,
    String eventId,
    String measurementId,
    LocalDate date,
    Instant measuredAt,
    Integer score,
    BigDecimal weightKg,
    BigDecimal bmi,
    BigDecimal bodyFatPercent,
    BigDecimal musclePercent,
    BigDecimal hydrationPercent,
    BigDecimal boneMassKg,
    Integer bmrKcal,
    Integer visceralFatLevel,
    BigDecimal subcutaneousFatPercent,
    BigDecimal proteinPercent,
    Integer metabolicAge,
    BigDecimal idealWeightKg,
    BigDecimal weightControlKg,
    BigDecimal fatMassKg,
    BigDecimal leanBodyMassKg,
    BigDecimal muscleMassKg,
    BigDecimal proteinMassKg,
    String bodyType,
    String source
) {
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final BigDecimal MIN_WEIGHT_KG = BigDecimal.ONE;
    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("700");
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    /**
     * Compact constructor with validation to ensure illegal states cannot be represented.
     */
    WeightMeasurement {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(measurementId, "measurementId cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        Objects.requireNonNull(measuredAt, "measuredAt cannot be null");
        Objects.requireNonNull(weightKg, "weightKg cannot be null");

        if (weightKg.compareTo(MIN_WEIGHT_KG) < 0) {
            throw new IllegalArgumentException("weightKg must be at least " + MIN_WEIGHT_KG + "kg");
        }
        if (weightKg.compareTo(MAX_WEIGHT_KG) > 0) {
            throw new IllegalArgumentException("weightKg cannot exceed " + MAX_WEIGHT_KG + "kg");
        }
        if (score != null && (score < MIN_SCORE || score > MAX_SCORE)) {
            throw new IllegalArgumentException("score must be between " + MIN_SCORE + " and " + MAX_SCORE);
        }
    }

    /**
     * Checks if this measurement has full body composition data.
     */
    boolean hasBodyCompositionData() {
        return bodyFatPercent != null && musclePercent != null;
    }

    /**
     * Checks if this is a healthy measurement based on score.
     */
    boolean isHealthyScore() {
        return score != null && score >= 70;
    }

    static WeightMeasurement create(
            String deviceId,
            String eventId,
            String measurementId,
            Instant measuredAt,
            Integer score,
            BigDecimal weightKg,
            BigDecimal bmi,
            BigDecimal bodyFatPercent,
            BigDecimal musclePercent,
            BigDecimal hydrationPercent,
            BigDecimal boneMassKg,
            Integer bmrKcal,
            Integer visceralFatLevel,
            BigDecimal subcutaneousFatPercent,
            BigDecimal proteinPercent,
            Integer metabolicAge,
            BigDecimal idealWeightKg,
            BigDecimal weightControlKg,
            BigDecimal fatMassKg,
            BigDecimal leanBodyMassKg,
            BigDecimal muscleMassKg,
            BigDecimal proteinMassKg,
            String bodyType,
            String source
    ) {
        LocalDate date = measuredAt.atZone(POLAND_ZONE).toLocalDate();
        return new WeightMeasurement(
                deviceId, eventId, measurementId, date, measuredAt, score, weightKg, bmi,
                bodyFatPercent, musclePercent, hydrationPercent, boneMassKg, bmrKcal,
                visceralFatLevel, subcutaneousFatPercent, proteinPercent, metabolicAge,
                idealWeightKg, weightControlKg, fatMassKg, leanBodyMassKg, muscleMassKg,
                proteinMassKg, bodyType, source
        );
    }
}
