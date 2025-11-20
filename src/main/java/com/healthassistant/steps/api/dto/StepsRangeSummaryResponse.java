package com.healthassistant.steps.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
@Schema(description = "Steps summary for a date range (week, month, or year)")
public record StepsRangeSummaryResponse(
    @JsonProperty("startDate")
    @Schema(description = "Start date of range", example = "2025-11-01")
    LocalDate startDate,

    @JsonProperty("endDate")
    @Schema(description = "End date of range", example = "2025-11-30")
    LocalDate endDate,

    @JsonProperty("totalSteps")
    @Schema(description = "Total steps for the entire range", example = "324500")
    Integer totalSteps,

    @JsonProperty("averageSteps")
    @Schema(description = "Average steps per day", example = "10817")
    Integer averageSteps,

    @JsonProperty("daysWithData")
    @Schema(description = "Number of days with at least 1 step", example = "28")
    Integer daysWithData,

    @JsonProperty("dailyStats")
    @Schema(description = "Daily statistics for each day in the range")
    List<DailyStats> dailyStats
) {
    @Builder
    public record DailyStats(
        @JsonProperty("date")
        @Schema(description = "Date", example = "2025-11-01")
        LocalDate date,

        @JsonProperty("totalSteps")
        @Schema(description = "Total steps for this day", example = "9850")
        Integer totalSteps,

        @JsonProperty("activeHoursCount")
        @Schema(description = "Number of active hours", example = "12")
        Integer activeHoursCount
    ) {}
}
