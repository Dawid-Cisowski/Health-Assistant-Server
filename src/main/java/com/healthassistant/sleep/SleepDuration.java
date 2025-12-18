package com.healthassistant.sleep;

import java.util.Objects;

record SleepDuration(int minutes) {

    static final SleepDuration ZERO = new SleepDuration(0);

    SleepDuration {
        if (minutes < 0) {
            throw new IllegalArgumentException("sleep duration cannot be negative");
        }
    }

    static SleepDuration of(int minutes) {
        return new SleepDuration(minutes);
    }

    SleepDuration add(SleepDuration other) {
        Objects.requireNonNull(other, "other cannot be null");
        return new SleepDuration(minutes + other.minutes);
    }

    boolean isLongerThan(SleepDuration other) {
        return minutes > other.minutes;
    }

    boolean isShorterThan(SleepDuration other) {
        return minutes < other.minutes;
    }

    SleepDuration max(SleepDuration other) {
        return isLongerThan(other) ? this : other;
    }

    SleepDuration min(SleepDuration other) {
        return isShorterThan(other) ? this : other;
    }

    boolean isPositive() {
        return minutes > 0;
    }
}
