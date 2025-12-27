package com.healthassistant.sleep.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Sleep summary for a date range (week, month, or year)")
public record SleepRangeSummaryResponse(
    @JsonProperty("startDate")
    @Schema(description = "Start date of range", example = "2025-11-01")
    LocalDate startDate,

    @JsonProperty("endDate")
    @Schema(description = "End date of range", example = "2025-11-30")
    LocalDate endDate,

    @JsonProperty("totalSleepMinutes")
    @Schema(description = "Total sleep minutes for the entire range", example = "14400")
    Integer totalSleepMinutes,

    @JsonProperty("averageSleepMinutes")
    @Schema(description = "Average sleep minutes per day", example = "480")
    Integer averageSleepMinutes,

    @JsonProperty("daysWithData")
    @Schema(description = "Number of days with at least one sleep session", example = "28")
    Integer daysWithData,

    @JsonProperty("dayWithMostSleep")
    @Schema(description = "Day with most sleep in the range")
    DayExtreme dayWithMostSleep,

    @JsonProperty("dayWithLeastSleep")
    @Schema(description = "Day with least sleep in the range")
    DayExtreme dayWithLeastSleep,

    @JsonProperty("totalLightSleepMinutes")
    @Schema(description = "Total light sleep minutes (future support)", example = "5400")
    Integer totalLightSleepMinutes,

    @JsonProperty("totalDeepSleepMinutes")
    @Schema(description = "Total deep sleep minutes (future support)", example = "3600")
    Integer totalDeepSleepMinutes,

    @JsonProperty("totalRemSleepMinutes")
    @Schema(description = "Total REM sleep minutes (future support)", example = "2700")
    Integer totalRemSleepMinutes,

    @JsonProperty("averageLightSleepMinutes")
    @Schema(description = "Average light sleep per day (future support)", example = "180")
    Integer averageLightSleepMinutes,

    @JsonProperty("averageDeepSleepMinutes")
    @Schema(description = "Average deep sleep per day (future support)", example = "120")
    Integer averageDeepSleepMinutes,

    @JsonProperty("averageRemSleepMinutes")
    @Schema(description = "Average REM sleep per day (future support)", example = "90")
    Integer averageRemSleepMinutes,

    @JsonProperty("dailyStats")
    @Schema(description = "Daily statistics for each day in the range")
    List<DailyStats> dailyStats
) {
    public record DayExtreme(
        @JsonProperty("date")
        @Schema(description = "Date", example = "2025-11-15")
        LocalDate date,

        @JsonProperty("sleepMinutes")
        @Schema(description = "Sleep minutes on this day", example = "540")
        Integer sleepMinutes
    ) {}

    public record DailyStats(
        @JsonProperty("date")
        @Schema(description = "Date", example = "2025-11-01")
        LocalDate date,

        @JsonProperty("totalSleepMinutes")
        @Schema(description = "Total sleep minutes for this day", example = "465")
        Integer totalSleepMinutes,

        @JsonProperty("sleepCount")
        @Schema(description = "Number of sleep sessions", example = "1")
        Integer sleepCount,

        @JsonProperty("lightSleepMinutes")
        @Schema(description = "Light sleep minutes (future support)", example = "175")
        Integer lightSleepMinutes,

        @JsonProperty("deepSleepMinutes")
        @Schema(description = "Deep sleep minutes (future support)", example = "115")
        Integer deepSleepMinutes,

        @JsonProperty("remSleepMinutes")
        @Schema(description = "REM sleep minutes (future support)", example = "85")
        Integer remSleepMinutes
    ) {}
}
