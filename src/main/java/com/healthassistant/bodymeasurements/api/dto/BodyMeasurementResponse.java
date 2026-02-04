package com.healthassistant.bodymeasurements.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Body measurement with all dimensions")
public record BodyMeasurementResponse(
    @JsonProperty("measurementId")
    @Schema(description = "Unique measurement identifier", example = "body-2025-01-15-abc123")
    String measurementId,

    @JsonProperty("date")
    @Schema(description = "Date of measurement", example = "2025-01-15")
    LocalDate date,

    @JsonProperty("measuredAt")
    @Schema(description = "Timestamp of measurement", example = "2025-01-15T08:00:00Z")
    Instant measuredAt,

    // Arms
    @JsonProperty("bicepsLeftCm")
    @Schema(description = "Left bicep circumference in cm", example = "38.5")
    BigDecimal bicepsLeftCm,

    @JsonProperty("bicepsRightCm")
    @Schema(description = "Right bicep circumference in cm", example = "39.0")
    BigDecimal bicepsRightCm,

    @JsonProperty("forearmLeftCm")
    @Schema(description = "Left forearm circumference in cm", example = "30.0")
    BigDecimal forearmLeftCm,

    @JsonProperty("forearmRightCm")
    @Schema(description = "Right forearm circumference in cm", example = "30.5")
    BigDecimal forearmRightCm,

    // Torso
    @JsonProperty("chestCm")
    @Schema(description = "Chest circumference in cm", example = "102.0")
    BigDecimal chestCm,

    @JsonProperty("waistCm")
    @Schema(description = "Waist circumference in cm", example = "82.0")
    BigDecimal waistCm,

    @JsonProperty("abdomenCm")
    @Schema(description = "Abdomen circumference in cm", example = "85.0")
    BigDecimal abdomenCm,

    @JsonProperty("hipsCm")
    @Schema(description = "Hips circumference in cm", example = "98.0")
    BigDecimal hipsCm,

    @JsonProperty("neckCm")
    @Schema(description = "Neck circumference in cm", example = "40.0")
    BigDecimal neckCm,

    @JsonProperty("shouldersCm")
    @Schema(description = "Shoulder width in cm", example = "120.0")
    BigDecimal shouldersCm,

    // Legs
    @JsonProperty("thighLeftCm")
    @Schema(description = "Left thigh circumference in cm", example = "58.0")
    BigDecimal thighLeftCm,

    @JsonProperty("thighRightCm")
    @Schema(description = "Right thigh circumference in cm", example = "58.5")
    BigDecimal thighRightCm,

    @JsonProperty("calfLeftCm")
    @Schema(description = "Left calf circumference in cm", example = "38.0")
    BigDecimal calfLeftCm,

    @JsonProperty("calfRightCm")
    @Schema(description = "Right calf circumference in cm", example = "38.5")
    BigDecimal calfRightCm,

    @JsonProperty("notes")
    @Schema(description = "Optional notes about the measurement", example = "Morning measurement")
    String notes
) {}
