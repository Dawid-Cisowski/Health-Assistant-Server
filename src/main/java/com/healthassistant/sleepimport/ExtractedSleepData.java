package com.healthassistant.sleepimport;

import java.time.Instant;
import java.time.LocalDate;

record ExtractedSleepData(
        boolean isValid,
        String validationError,
        LocalDate sleepDate,
        Instant sleepStart,
        Instant sleepEnd,
        Integer totalSleepMinutes,
        Integer sleepScore,
        Phases phases,
        String qualityLabel,
        double confidence,
        Long promptTokens,
        Long completionTokens
) {

    record Phases(
            Integer lightSleepMinutes,
            Integer deepSleepMinutes,
            Integer remSleepMinutes,
            Integer awakeMinutes
    ) {
        static Phases empty() {
            return new Phases(null, null, null, null);
        }

        boolean hasData() {
            return lightSleepMinutes != null || deepSleepMinutes != null
                    || remSleepMinutes != null || awakeMinutes != null;
        }
    }

    static ExtractedSleepData valid(
            LocalDate sleepDate,
            Instant sleepStart,
            Instant sleepEnd,
            Integer totalSleepMinutes,
            Integer sleepScore,
            Phases phases,
            String qualityLabel,
            double confidence
    ) {
        return new ExtractedSleepData(
                true, null, sleepDate, sleepStart, sleepEnd,
                totalSleepMinutes, sleepScore, phases, qualityLabel, confidence, null, null
        );
    }

    static ExtractedSleepData validWithTokens(
            LocalDate sleepDate,
            Instant sleepStart,
            Instant sleepEnd,
            Integer totalSleepMinutes,
            Integer sleepScore,
            Phases phases,
            String qualityLabel,
            double confidence,
            Long promptTokens,
            Long completionTokens
    ) {
        return new ExtractedSleepData(
                true, null, sleepDate, sleepStart, sleepEnd,
                totalSleepMinutes, sleepScore, phases, qualityLabel, confidence,
                promptTokens, completionTokens
        );
    }

    static ExtractedSleepData invalid(String error, double confidence) {
        return new ExtractedSleepData(
                false, error, null, null, null, null, null, Phases.empty(), null, confidence, null, null
        );
    }
}
