package com.healthassistant.dailysummary;

import java.time.LocalDate;

record GenerateDailySummaryCommand(LocalDate date) {
    public static GenerateDailySummaryCommand forDate(LocalDate date) {
        return new GenerateDailySummaryCommand(date);
    }
}
