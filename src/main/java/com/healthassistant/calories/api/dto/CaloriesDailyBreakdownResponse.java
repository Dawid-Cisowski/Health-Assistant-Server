package com.healthassistant.calories.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Daily calories breakdown with hourly data for chart view")
public record CaloriesDailyBreakdownResponse(
    @JsonProperty("date")
    @Schema(description = "Date for the breakdown", example = "2025-11-25")
    LocalDate date,

    @JsonProperty("totalCalories")
    @Schema(description = "Total calories for the day", example = "2450.5")
    Double totalCalories,

    @JsonProperty("firstCalorieTime")
    @Schema(description = "Time of first recorded calories", example = "2025-11-25T06:30:00Z")
    Instant firstCalorieTime,

    @JsonProperty("lastCalorieTime")
    @Schema(description = "Time of last recorded calories", example = "2025-11-25T22:15:00Z")
    Instant lastCalorieTime,

    @JsonProperty("mostActiveHour")
    @Schema(description = "Hour with most calories burned (0-23)", example = "18")
    Integer mostActiveHour,

    @JsonProperty("mostActiveHourCalories")
    @Schema(description = "Calories in most active hour", example = "284.5")
    Double mostActiveHourCalories,

    @JsonProperty("activeHoursCount")
    @Schema(description = "Number of hours with at least some calories burned", example = "14")
    Integer activeHoursCount,

    @JsonProperty("hourlyBreakdown")
    @Schema(description = "24-hour breakdown (0-23)")
    List<HourlyCalories> hourlyBreakdown
) {
    public record HourlyCalories(
        @JsonProperty("hour")
        @Schema(description = "Hour of day (0-23)", example = "14")
        Integer hour,

        @JsonProperty("calories")
        @Schema(description = "Calories burned in this hour", example = "184.2")
        Double calories
    ) {}
}
