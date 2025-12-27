package com.healthassistant.activity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Schema(description = "Daily activity breakdown with hourly data for chart view")
public record ActivityDailyBreakdownResponse(
    @JsonProperty("date")
    @Schema(description = "Date for the breakdown", example = "2025-11-25")
    LocalDate date,

    @JsonProperty("totalActiveMinutes")
    @Schema(description = "Total active minutes for the day", example = "45")
    Integer totalActiveMinutes,

    @JsonProperty("firstActivityTime")
    @Schema(description = "Time of first recorded activity", example = "2025-11-25T06:30:00Z")
    Instant firstActivityTime,

    @JsonProperty("lastActivityTime")
    @Schema(description = "Time of last recorded activity", example = "2025-11-25T22:15:00Z")
    Instant lastActivityTime,

    @JsonProperty("mostActiveHour")
    @Schema(description = "Hour with most active minutes (0-23)", example = "18")
    Integer mostActiveHour,

    @JsonProperty("mostActiveHourMinutes")
    @Schema(description = "Active minutes in most active hour", example = "15")
    Integer mostActiveHourMinutes,

    @JsonProperty("activeHoursCount")
    @Schema(description = "Number of hours with at least some activity", example = "8")
    Integer activeHoursCount,

    @JsonProperty("hourlyBreakdown")
    @Schema(description = "24-hour breakdown (0-23)")
    List<HourlyActivity> hourlyBreakdown
) {
    public record HourlyActivity(
        @JsonProperty("hour")
        @Schema(description = "Hour of day (0-23)", example = "14")
        Integer hour,

        @JsonProperty("activeMinutes")
        @Schema(description = "Active minutes in this hour", example = "12")
        Integer activeMinutes
    ) {}

    public static ActivityDailyBreakdownResponse empty(LocalDate date) {
        return of(date, 0, null, null, null, 0, 0, Map.of());
    }

    public static ActivityDailyBreakdownResponse of(
            LocalDate date,
            int totalActiveMinutes,
            Instant firstActivityTime,
            Instant lastActivityTime,
            Integer mostActiveHour,
            int mostActiveHourMinutes,
            int activeHoursCount,
            Map<Integer, Integer> hourlyMinutes
    ) {
        List<HourlyActivity> hourlyBreakdown = IntStream.range(0, 24)
            .mapToObj(hour -> new HourlyActivity(hour, hourlyMinutes.getOrDefault(hour, 0)))
            .toList();

        return new ActivityDailyBreakdownResponse(
            date,
            totalActiveMinutes,
            firstActivityTime,
            lastActivityTime,
            mostActiveHour,
            mostActiveHourMinutes,
            activeHoursCount,
            hourlyBreakdown
        );
    }
}
