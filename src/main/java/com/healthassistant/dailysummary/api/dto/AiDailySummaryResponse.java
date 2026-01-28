package com.healthassistant.dailysummary.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiDailySummaryResponse(
    LocalDate date,
    String summary,
    boolean dataAvailable,
    Long promptTokens,
    Long completionTokens
) {
    public AiDailySummaryResponse(LocalDate date, String summary, boolean dataAvailable) {
        this(date, summary, dataAvailable, null, null);
    }

    public static AiDailySummaryResponse noData(LocalDate date) {
        return new AiDailySummaryResponse(date, "Brak danych na ten dzien", false, null, null);
    }

    public static AiDailySummaryResponse withTokens(LocalDate date, String summary, boolean dataAvailable,
                                                     Long promptTokens, Long completionTokens) {
        return new AiDailySummaryResponse(date, summary, dataAvailable, promptTokens, completionTokens);
    }
}
