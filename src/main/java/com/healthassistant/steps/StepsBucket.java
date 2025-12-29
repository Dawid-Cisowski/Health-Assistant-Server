package com.healthassistant.steps;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

record StepsBucket(
        String deviceId,
        LocalDate date,
        int hour,
        StepCount count,
        Instant bucketStart,
        Instant bucketEnd
) {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    StepsBucket {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        Objects.requireNonNull(count, "count cannot be null");
        Objects.requireNonNull(bucketStart, "bucketStart cannot be null");
        Objects.requireNonNull(bucketEnd, "bucketEnd cannot be null");
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be between 0 and 23");
        }
        if (!bucketStart.isBefore(bucketEnd)) {
            throw new IllegalArgumentException("bucketStart must be before bucketEnd");
        }
    }

    static StepsBucket create(String deviceId, Instant bucketStart, Instant bucketEnd, int stepCount) {
        var startZoned = bucketStart.atZone(POLAND_ZONE);
        LocalDate date = startZoned.toLocalDate();
        int hour = startZoned.getHour();
        StepCount count = StepCount.of(stepCount);
        return new StepsBucket(deviceId, date, hour, count, bucketStart, bucketEnd);
    }

    int stepCount() {
        return count.value();
    }

    boolean hasSteps() {
        return count.isPositive();
    }
}
