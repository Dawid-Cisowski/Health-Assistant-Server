package com.healthassistant.steps.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Daily steps breakdown with hourly data for dashboard view")
public record StepsDailyBreakdownResponse(
    @JsonProperty("date")
    @Schema(description = "Date for the breakdown", example = "2025-11-19")
    LocalDate date,

    @JsonProperty("totalSteps")
    @Schema(description = "Total steps for the day", example = "12450")
    Integer totalSteps,

    @JsonProperty("firstStepTime")
    @Schema(description = "Time of first recorded steps", example = "2025-11-19T06:30:00Z")
    Instant firstStepTime,

    @JsonProperty("lastStepTime")
    @Schema(description = "Time of last recorded steps", example = "2025-11-19T22:15:00Z")
    Instant lastStepTime,

    @JsonProperty("mostActiveHour")
    @Schema(description = "Hour with most steps (0-23)", example = "18")
    Integer mostActiveHour,

    @JsonProperty("mostActiveHourSteps")
    @Schema(description = "Steps in most active hour", example = "2840")
    Integer mostActiveHourSteps,

    @JsonProperty("activeHoursCount")
    @Schema(description = "Number of hours with at least 1 step", example = "14")
    Integer activeHoursCount,

    @JsonProperty("hourlyBreakdown")
    @Schema(description = "24-hour breakdown (0-23)")
    List<HourlySteps> hourlyBreakdown
) {
    public record HourlySteps(
        @JsonProperty("hour")
        @Schema(description = "Hour of day (0-23)", example = "14")
        Integer hour,

        @JsonProperty("steps")
        @Schema(description = "Steps in this hour", example = "1842")
        Integer steps
    ) {}
}
