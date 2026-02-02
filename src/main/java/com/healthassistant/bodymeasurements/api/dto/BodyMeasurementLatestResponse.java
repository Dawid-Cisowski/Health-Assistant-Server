package com.healthassistant.bodymeasurements.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Latest body measurement with trend data")
public record BodyMeasurementLatestResponse(
    @JsonProperty("measurement")
    @Schema(description = "Latest body measurement")
    BodyMeasurementResponse measurement,

    @JsonProperty("trends")
    @Schema(description = "Body measurement trends compared to previous measurements")
    TrendData trends
) {
    @Schema(description = "Body measurement trend comparisons")
    public record TrendData(
        @JsonProperty("bicepsLeftChangeVsPrevious")
        @Schema(description = "Left bicep change vs previous measurement in cm", example = "0.5")
        BigDecimal bicepsLeftChangeVsPrevious,

        @JsonProperty("bicepsRightChangeVsPrevious")
        @Schema(description = "Right bicep change vs previous measurement in cm", example = "0.5")
        BigDecimal bicepsRightChangeVsPrevious,

        @JsonProperty("chestChangeVsPrevious")
        @Schema(description = "Chest change vs previous measurement in cm", example = "1.0")
        BigDecimal chestChangeVsPrevious,

        @JsonProperty("waistChangeVsPrevious")
        @Schema(description = "Waist change vs previous measurement in cm", example = "-1.5")
        BigDecimal waistChangeVsPrevious,

        @JsonProperty("thighLeftChangeVsPrevious")
        @Schema(description = "Left thigh change vs previous measurement in cm", example = "0.5")
        BigDecimal thighLeftChangeVsPrevious,

        @JsonProperty("thighRightChangeVsPrevious")
        @Schema(description = "Right thigh change vs previous measurement in cm", example = "0.5")
        BigDecimal thighRightChangeVsPrevious,

        @JsonProperty("measurementsInLast30Days")
        @Schema(description = "Number of measurements in the last 30 days", example = "4")
        Integer measurementsInLast30Days
    ) {}
}
