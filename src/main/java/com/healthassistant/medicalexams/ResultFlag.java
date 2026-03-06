package com.healthassistant.medicalexams;

enum ResultFlag {
    UNKNOWN, NORMAL, WARNING, LOW, HIGH, CRITICAL_HIGH, CRITICAL_LOW;

    boolean isAbnormal() {
        return this == HIGH || this == LOW || this == CRITICAL_HIGH || this == CRITICAL_LOW;
    }
}
