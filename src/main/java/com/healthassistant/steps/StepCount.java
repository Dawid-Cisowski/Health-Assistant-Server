package com.healthassistant.steps;

record StepCount(int value) {

    static final StepCount ZERO = new StepCount(0);

    StepCount {
        if (value < 0) {
            throw new IllegalArgumentException("step count cannot be negative");
        }
    }

    static StepCount of(int value) {
        return new StepCount(value);
    }

    StepCount add(StepCount other) {
        return new StepCount(value + other.value);
    }

    boolean isPositive() {
        return value > 0;
    }

    boolean isGreaterThan(StepCount other) {
        return value > other.value;
    }
}
