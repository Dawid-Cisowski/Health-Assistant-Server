package com.healthassistant.healthevents;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record HeartRateStats(Integer min, Double avg, Integer max) {

    HeartRateStats {
        Objects.requireNonNull(min, "min cannot be null");
        Objects.requireNonNull(avg, "avg cannot be null");
        Objects.requireNonNull(max, "max cannot be null");
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        if (avg < min) {
            throw new IllegalArgumentException("avg cannot be less than min");
        }
        if (avg > max) {
            throw new IllegalArgumentException("avg cannot be greater than max");
        }
    }

    static List<EventValidationError> validate(Integer min, Double avg, Integer max) {
        List<EventValidationError> errors = new ArrayList<>();

        if (min != null && max != null && min > max) {
            errors.add(EventValidationError.invalidValue("min", "cannot be greater than max"));
        }
        if (avg != null && min != null && avg < min) {
            errors.add(EventValidationError.invalidValue("avg", "cannot be less than min"));
        }
        if (avg != null && max != null && avg > max) {
            errors.add(EventValidationError.invalidValue("avg", "cannot be greater than max"));
        }

        return errors;
    }
}
