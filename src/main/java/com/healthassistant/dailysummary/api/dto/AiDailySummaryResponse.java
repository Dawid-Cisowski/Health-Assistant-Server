package com.healthassistant.dailysummary.api.dto;

import java.time.LocalDate;

public record AiDailySummaryResponse(
    LocalDate date,
    String summary,
    boolean dataAvailable
) {
    public static AiDailySummaryResponse noData(LocalDate date) {
        return new AiDailySummaryResponse(date, "Brak danych na ten dzien", false);
    }
}
