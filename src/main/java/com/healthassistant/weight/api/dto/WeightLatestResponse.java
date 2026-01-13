package com.healthassistant.weight.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Latest weight measurement with trend data")
public record WeightLatestResponse(
    @JsonProperty("measurement")
    @Schema(description = "Latest weight measurement")
    WeightMeasurementResponse measurement,

    @JsonProperty("trends")
    @Schema(description = "Weight trends compared to previous measurements")
    TrendData trends
) {
    @Schema(description = "Weight trend comparisons")
    public record TrendData(
        @JsonProperty("weightChangeVsPrevious")
        @Schema(description = "Weight change vs previous measurement in kg", example = "-0.5")
        BigDecimal weightChangeVsPrevious,

        @JsonProperty("weightChange7Days")
        @Schema(description = "Weight change vs 7 days ago in kg", example = "-1.2")
        BigDecimal weightChange7Days,

        @JsonProperty("weightChange30Days")
        @Schema(description = "Weight change vs 30 days ago in kg", example = "-2.5")
        BigDecimal weightChange30Days,

        @JsonProperty("bodyFatChangeVsPrevious")
        @Schema(description = "Body fat % change vs previous measurement", example = "-0.3")
        BigDecimal bodyFatChangeVsPrevious,

        @JsonProperty("muscleChangeVsPrevious")
        @Schema(description = "Muscle % change vs previous measurement", example = "0.2")
        BigDecimal muscleChangeVsPrevious,

        @JsonProperty("measurementsInLast30Days")
        @Schema(description = "Number of measurements in the last 30 days", example = "4")
        Integer measurementsInLast30Days
    ) {}
}
