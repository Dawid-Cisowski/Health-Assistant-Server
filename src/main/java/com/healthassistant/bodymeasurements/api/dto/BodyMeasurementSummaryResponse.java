package com.healthassistant.bodymeasurements.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Summary of latest body measurements with changes - for dashboard display")
public record BodyMeasurementSummaryResponse(
    @JsonProperty("lastMeasuredAt")
    @Schema(description = "Timestamp of most recent measurement", example = "2025-01-15T08:00:00Z")
    Instant lastMeasuredAt,

    @JsonProperty("measurements")
    @Schema(description = "Map of body part to measurement data with change vs previous")
    Map<String, MeasurementWithChange> measurements
) {
    @Schema(description = "Single body part measurement with change information")
    public record MeasurementWithChange(
        @JsonProperty("value")
        @Schema(description = "Current measurement value in cm", example = "38.5")
        BigDecimal value,

        @JsonProperty("change")
        @Schema(description = "Change vs previous measurement in cm (null if no previous)", example = "0.5")
        BigDecimal change,

        @JsonProperty("unit")
        @Schema(description = "Unit of measurement", example = "cm")
        String unit
    ) {
        public static MeasurementWithChange of(BigDecimal value, BigDecimal change) {
            return new MeasurementWithChange(value, change, "cm");
        }
    }
}
