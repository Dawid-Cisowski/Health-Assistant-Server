package com.healthassistant.activity;

record ActiveMinutes(int value) {

    static final ActiveMinutes ZERO = new ActiveMinutes(0);

    ActiveMinutes {
        if (value < 0) {
            throw new IllegalArgumentException("active minutes cannot be negative");
        }
    }

    static ActiveMinutes of(int value) {
        return new ActiveMinutes(value);
    }

    boolean isPositive() {
        return value > 0;
    }

}
