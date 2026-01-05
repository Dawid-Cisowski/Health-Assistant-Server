package com.healthassistant.dailysummary;

import java.time.LocalDate;
import java.util.Objects;

record GenerateDailySummaryCommand(String deviceId, LocalDate date) {

    GenerateDailySummaryCommand {
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("date must not be in the future");
        }
    }

    static GenerateDailySummaryCommand forDeviceAndDate(String deviceId, LocalDate date) {
        return new GenerateDailySummaryCommand(deviceId, date);
    }
}
