package com.healthassistant.sleep;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

record SleepSession(
        String sleepId,
        String eventId,
        LocalDate date,
        Instant sleepStart,
        Instant sleepEnd,
        SleepDuration duration,
        SleepStages stages,
        Integer sleepScore,
        String originPackage
) {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    SleepSession {
        Objects.requireNonNull(sleepId, "sleepId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        Objects.requireNonNull(sleepStart, "sleepStart cannot be null");
        Objects.requireNonNull(sleepEnd, "sleepEnd cannot be null");
        Objects.requireNonNull(duration, "duration cannot be null");
        if (!sleepStart.isBefore(sleepEnd)) {
            throw new IllegalArgumentException("sleepStart must be before sleepEnd");
        }
    }

    static SleepSession create(
            String sleepId,
            String eventId,
            Instant sleepStart,
            Instant sleepEnd,
            int totalMinutes,
            String originPackage
    ) {
        LocalDate date = sleepEnd.atZone(POLAND_ZONE).toLocalDate();
        SleepDuration duration = SleepDuration.of(totalMinutes);
        return new SleepSession(sleepId, eventId, date, sleepStart, sleepEnd, duration, SleepStages.EMPTY, null, originPackage);
    }

    static SleepSession create(
            String sleepId,
            String eventId,
            Instant sleepStart,
            Instant sleepEnd,
            int totalMinutes,
            SleepStages stages,
            String originPackage
    ) {
        LocalDate date = sleepEnd.atZone(POLAND_ZONE).toLocalDate();
        SleepDuration duration = SleepDuration.of(totalMinutes);
        return new SleepSession(sleepId, eventId, date, sleepStart, sleepEnd, duration, stages, null, originPackage);
    }

    static SleepSession create(
            String sleepId,
            String eventId,
            Instant sleepStart,
            Instant sleepEnd,
            int totalMinutes,
            SleepStages stages,
            Integer sleepScore,
            String originPackage
    ) {
        LocalDate date = sleepEnd.atZone(POLAND_ZONE).toLocalDate();
        SleepDuration duration = SleepDuration.of(totalMinutes);
        return new SleepSession(sleepId, eventId, date, sleepStart, sleepEnd, duration, stages, sleepScore, originPackage);
    }

    boolean hasValidDuration() {
        return duration.isPositive();
    }

    int durationMinutes() {
        return duration.minutes();
    }
}
