package com.healthassistant.workout;

import java.math.BigDecimal;
import java.util.Objects;

record Volume(BigDecimal kilograms) {

    static final Volume ZERO = new Volume(BigDecimal.ZERO);

    Volume {
        Objects.requireNonNull(kilograms, "kilograms cannot be null");
        if (kilograms.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("volume cannot be negative");
        }
    }

    static Volume calculate(Weight weight, Reps reps) {
        BigDecimal value = weight.kilograms().multiply(BigDecimal.valueOf(reps.count()));
        return new Volume(value);
    }

    Volume add(Volume other) {
        return new Volume(kilograms.add(other.kilograms));
    }
}
