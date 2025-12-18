package com.healthassistant.calories;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

record CaloriesBucket(
        String deviceId,
        LocalDate date,
        int hour,
        CaloriesAmount amount,
        Instant bucketStart,
        Instant bucketEnd
) {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    CaloriesBucket {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(bucketStart, "bucketStart cannot be null");
        Objects.requireNonNull(bucketEnd, "bucketEnd cannot be null");
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be between 0 and 23");
        }
        if (!bucketStart.isBefore(bucketEnd)) {
            throw new IllegalArgumentException("bucketStart must be before bucketEnd");
        }
    }

    static CaloriesBucket create(String deviceId, Instant bucketStart, Instant bucketEnd, double energyKcal) {
        var startZoned = bucketStart.atZone(POLAND_ZONE);
        LocalDate date = startZoned.toLocalDate();
        int hour = startZoned.getHour();
        CaloriesAmount amount = CaloriesAmount.of(energyKcal);
        return new CaloriesBucket(deviceId, date, hour, amount, bucketStart, bucketEnd);
    }

    double caloriesKcal() {
        return amount.kcal();
    }

    boolean hasCalories() {
        return amount.isPositive();
    }
}
