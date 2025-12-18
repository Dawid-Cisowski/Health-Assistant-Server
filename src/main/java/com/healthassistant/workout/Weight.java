package com.healthassistant.workout;

import java.math.BigDecimal;
import java.util.Objects;

record Weight(BigDecimal kilograms) {

    static final Weight ZERO = new Weight(BigDecimal.ZERO);

    Weight {
        Objects.requireNonNull(kilograms, "kilograms cannot be null");
        if (kilograms.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("weight cannot be negative");
        }
    }

    static Weight of(double kg) {
        return new Weight(BigDecimal.valueOf(kg));
    }

    static Weight of(BigDecimal kg) {
        return new Weight(kg);
    }

    boolean isGreaterThan(Weight other) {
        return kilograms.compareTo(other.kilograms) > 0;
    }

    Weight max(Weight other) {
        return isGreaterThan(other) ? this : other;
    }
}
