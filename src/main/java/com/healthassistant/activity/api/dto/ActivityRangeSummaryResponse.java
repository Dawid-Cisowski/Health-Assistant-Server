package com.healthassistant.activity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
@Schema(description = "Activity summary for a date range (week, month, or year)")
public record ActivityRangeSummaryResponse(
    @JsonProperty("startDate")
    @Schema(description = "Start date of range", example = "2025-11-01")
    LocalDate startDate,

    @JsonProperty("endDate")
    @Schema(description = "End date of range", example = "2025-11-30")
    LocalDate endDate,

    @JsonProperty("totalActiveMinutes")
    @Schema(description = "Total active minutes for the entire range", example = "1350")
    Integer totalActiveMinutes,

    @JsonProperty("averageActiveMinutes")
    @Schema(description = "Average active minutes per day", example = "45")
    Integer averageActiveMinutes,

    @JsonProperty("daysWithData")
    @Schema(description = "Number of days with at least some activity data", example = "28")
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

        @JsonProperty("totalActiveMinutes")
        @Schema(description = "Total active minutes for this day", example = "42")
        Integer totalActiveMinutes,

        @JsonProperty("activeHoursCount")
        @Schema(description = "Number of active hours", example = "6")
        Integer activeHoursCount
    ) {}
}
