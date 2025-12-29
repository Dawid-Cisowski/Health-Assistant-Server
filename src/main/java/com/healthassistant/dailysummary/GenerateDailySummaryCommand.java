package com.healthassistant.dailysummary;

import java.time.LocalDate;

record GenerateDailySummaryCommand(String deviceId, LocalDate date) {
    public static GenerateDailySummaryCommand forDeviceAndDate(String deviceId, LocalDate date) {
        return new GenerateDailySummaryCommand(deviceId, date);
    }
}
