package com.healthassistant.bodymeasurements.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "History of a specific body part measurement for charting")
public record BodyPartHistoryResponse(
    @JsonProperty("bodyPart")
    @Schema(description = "The body part being tracked", example = "biceps-left")
    BodyPart bodyPart,

    @JsonProperty("unit")
    @Schema(description = "Unit of measurement", example = "cm")
    String unit,

    @JsonProperty("dataPoints")
    @Schema(description = "Historical data points")
    List<DataPoint> dataPoints,

    @JsonProperty("statistics")
    @Schema(description = "Summary statistics for the range")
    Statistics statistics
) {
    @Schema(description = "Single data point for charting")
    public record DataPoint(
        @JsonProperty("date")
        @Schema(description = "Date of measurement", example = "2025-01-15")
        LocalDate date,

        @JsonProperty("value")
        @Schema(description = "Measurement value in cm", example = "38.5")
        BigDecimal value
    ) {}

    @Schema(description = "Summary statistics for the body part history")
    public record Statistics(
        @JsonProperty("min")
        @Schema(description = "Minimum value in range", example = "36.0")
        BigDecimal min,

        @JsonProperty("max")
        @Schema(description = "Maximum value in range", example = "38.5")
        BigDecimal max,

        @JsonProperty("change")
        @Schema(description = "Total change from first to last measurement", example = "2.5")
        BigDecimal change,

        @JsonProperty("changePercent")
        @Schema(description = "Percentage change from first to last measurement", example = "6.9")
        BigDecimal changePercent
    ) {}

    public static BodyPartHistoryResponse empty(BodyPart bodyPart) {
        return new BodyPartHistoryResponse(
                bodyPart,
                "cm",
                List.of(),
                new Statistics(null, null, null, null)
        );
    }
}
