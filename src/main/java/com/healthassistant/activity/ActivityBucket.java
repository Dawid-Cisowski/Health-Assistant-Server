package com.healthassistant.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

record ActivityBucket(
        String deviceId,
        LocalDate date,
        int hour,
        ActiveMinutes minutes,
        Instant bucketStart,
        Instant bucketEnd
) {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    ActivityBucket {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        Objects.requireNonNull(minutes, "minutes cannot be null");
        Objects.requireNonNull(bucketStart, "bucketStart cannot be null");
        Objects.requireNonNull(bucketEnd, "bucketEnd cannot be null");
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be between 0 and 23");
        }
        if (!bucketStart.isBefore(bucketEnd)) {
            throw new IllegalArgumentException("bucketStart must be before bucketEnd");
        }
    }

    static ActivityBucket create(String deviceId, Instant bucketStart, Instant bucketEnd, int activeMinutes) {
        var startZoned = bucketStart.atZone(POLAND_ZONE);
        LocalDate date = startZoned.toLocalDate();
        int hour = startZoned.getHour();
        ActiveMinutes minutes = ActiveMinutes.of(activeMinutes);
        return new ActivityBucket(deviceId, date, hour, minutes, bucketStart, bucketEnd);
    }

    int activeMinutes() {
        return minutes.value();
    }

    boolean hasActivity() {
        return minutes.isPositive();
    }
}
