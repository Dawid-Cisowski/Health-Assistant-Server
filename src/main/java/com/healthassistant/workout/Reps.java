package com.healthassistant.workout;

record Reps(int count) {

    Reps {
        if (count < 0) {
            throw new IllegalArgumentException("reps cannot be negative");
        }
    }

    static Reps of(int count) {
        return new Reps(count);
    }
}
