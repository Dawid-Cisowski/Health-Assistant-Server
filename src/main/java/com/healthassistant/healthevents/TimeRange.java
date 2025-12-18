package com.healthassistant.healthevents;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

record TimeRange(Instant start, Instant end) {

    TimeRange {
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(end, "end cannot be null");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
    }

    static Optional<TimeRange> ofNullable(Instant start, Instant end) {
        if (start == null || end == null) {
            return Optional.empty();
        }
        if (!start.isBefore(end)) {
            return Optional.empty();
        }
        return Optional.of(new TimeRange(start, end));
    }

    static Optional<EventValidationError> validate(Instant start, Instant end, String endFieldName, String startFieldName) {
        if (start == null || end == null) {
            return Optional.empty();
        }
        return start.isBefore(end)
                ? Optional.empty()
                : Optional.of(EventValidationError.invalidValue(endFieldName, "must be after " + startFieldName));
    }

    Duration duration() {
        return Duration.between(start, end);
    }

    long durationMinutes() {
        return duration().toMinutes();
    }

    boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
