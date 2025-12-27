package com.healthassistant.calories.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Calories summary for a date range (week, month, or year)")
public record CaloriesRangeSummaryResponse(
    @JsonProperty("startDate")
    @Schema(description = "Start date of range", example = "2025-11-01")
    LocalDate startDate,

    @JsonProperty("endDate")
    @Schema(description = "End date of range", example = "2025-11-30")
    LocalDate endDate,

    @JsonProperty("totalCalories")
    @Schema(description = "Total calories for the entire range", example = "73500.5")
    Double totalCalories,

    @JsonProperty("averageCalories")
    @Schema(description = "Average calories per day", example = "2450.0")
    Double averageCalories,

    @JsonProperty("daysWithData")
    @Schema(description = "Number of days with at least some calories data", example = "28")
    Integer daysWithData,

    @JsonProperty("dailyStats")
    @Schema(description = "Daily statistics for each day in the range")
    List<DailyStats> dailyStats
) {
    public record DailyStats(
        @JsonProperty("date")
        @Schema(description = "Date", example = "2025-11-01")
        LocalDate date,

        @JsonProperty("totalCalories")
        @Schema(description = "Total calories for this day", example = "2350.5")
        Double totalCalories,

        @JsonProperty("activeHoursCount")
        @Schema(description = "Number of active hours", example = "12")
        Integer activeHoursCount
    ) {}
}
