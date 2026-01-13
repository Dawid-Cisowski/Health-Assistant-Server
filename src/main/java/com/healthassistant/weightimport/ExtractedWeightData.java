package com.healthassistant.weightimport;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record ExtractedWeightData(
        boolean isValid,
        String validationError,
        LocalDate measurementDate,
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
        double confidence
) {

    static ExtractedWeightData valid(
            LocalDate measurementDate,
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
            double confidence
    ) {
        return new ExtractedWeightData(
                true, null, measurementDate, measuredAt, score, weightKg, bmi,
                bodyFatPercent, musclePercent, hydrationPercent, boneMassKg, bmrKcal,
                visceralFatLevel, subcutaneousFatPercent, proteinPercent, metabolicAge,
                idealWeightKg, weightControlKg, fatMassKg, leanBodyMassKg, muscleMassKg,
                proteinMassKg, bodyType, confidence
        );
    }

    static ExtractedWeightData invalid(String error, double confidence) {
        return new ExtractedWeightData(
                false, error, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, confidence
        );
    }
}
