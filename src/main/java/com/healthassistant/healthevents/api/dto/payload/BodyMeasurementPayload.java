package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(
    description = "Payload for BodyMeasurementRecorded.v1 - body dimensions measurement (biceps, waist, chest, etc.)",
    example = """
        {
          "measurementId": "body-2025-01-15-abc123",
          "measuredAt": "2025-01-15T08:00:00Z",
          "bicepsLeftCm": 38.5,
          "bicepsRightCm": 39.0,
          "forearmLeftCm": 30.0,
          "forearmRightCm": 30.5,
          "chestCm": 102.0,
          "waistCm": 82.0,
          "abdomenCm": 85.0,
          "hipsCm": 98.0,
          "neckCm": 40.0,
          "shouldersCm": 120.0,
          "thighLeftCm": 58.0,
          "thighRightCm": 58.5,
          "calfLeftCm": 38.0,
          "calfRightCm": 38.5,
          "notes": "Morning measurement, relaxed muscles"
        }
        """
)
public record BodyMeasurementPayload(
    @NotBlank(message = "measurementId is required")
    @JsonProperty("measurementId")
    @Schema(description = "Unique identifier for this measurement", example = "body-2025-01-15-abc123")
    String measurementId,

    @NotNull(message = "measuredAt is required")
    @JsonProperty("measuredAt")
    @Schema(description = "Timestamp when measurement was taken (ISO-8601 UTC)", example = "2025-01-15T08:00:00Z")
    Instant measuredAt,

    // Arms
    @DecimalMin(value = "10.0", message = "bicepsLeftCm must be at least 10 cm")
    @DecimalMax(value = "100.0", message = "bicepsLeftCm must be at most 100 cm")
    @JsonProperty("bicepsLeftCm")
    @Schema(description = "Left bicep circumference in cm", example = "38.5", minimum = "10", maximum = "100")
    BigDecimal bicepsLeftCm,

    @DecimalMin(value = "10.0", message = "bicepsRightCm must be at least 10 cm")
    @DecimalMax(value = "100.0", message = "bicepsRightCm must be at most 100 cm")
    @JsonProperty("bicepsRightCm")
    @Schema(description = "Right bicep circumference in cm", example = "39.0", minimum = "10", maximum = "100")
    BigDecimal bicepsRightCm,

    @DecimalMin(value = "10.0", message = "forearmLeftCm must be at least 10 cm")
    @DecimalMax(value = "80.0", message = "forearmLeftCm must be at most 80 cm")
    @JsonProperty("forearmLeftCm")
    @Schema(description = "Left forearm circumference in cm", example = "30.0", minimum = "10", maximum = "80")
    BigDecimal forearmLeftCm,

    @DecimalMin(value = "10.0", message = "forearmRightCm must be at least 10 cm")
    @DecimalMax(value = "80.0", message = "forearmRightCm must be at most 80 cm")
    @JsonProperty("forearmRightCm")
    @Schema(description = "Right forearm circumference in cm", example = "30.5", minimum = "10", maximum = "80")
    BigDecimal forearmRightCm,

    // Torso
    @DecimalMin(value = "40.0", message = "chestCm must be at least 40 cm")
    @DecimalMax(value = "300.0", message = "chestCm must be at most 300 cm")
    @JsonProperty("chestCm")
    @Schema(description = "Chest circumference in cm", example = "102.0", minimum = "40", maximum = "300")
    BigDecimal chestCm,

    @DecimalMin(value = "40.0", message = "waistCm must be at least 40 cm")
    @DecimalMax(value = "300.0", message = "waistCm must be at most 300 cm")
    @JsonProperty("waistCm")
    @Schema(description = "Waist circumference in cm", example = "82.0", minimum = "40", maximum = "300")
    BigDecimal waistCm,

    @DecimalMin(value = "40.0", message = "abdomenCm must be at least 40 cm")
    @DecimalMax(value = "300.0", message = "abdomenCm must be at most 300 cm")
    @JsonProperty("abdomenCm")
    @Schema(description = "Abdomen circumference in cm", example = "85.0", minimum = "40", maximum = "300")
    BigDecimal abdomenCm,

    @DecimalMin(value = "40.0", message = "hipsCm must be at least 40 cm")
    @DecimalMax(value = "300.0", message = "hipsCm must be at most 300 cm")
    @JsonProperty("hipsCm")
    @Schema(description = "Hips circumference in cm", example = "98.0", minimum = "40", maximum = "300")
    BigDecimal hipsCm,

    @DecimalMin(value = "20.0", message = "neckCm must be at least 20 cm")
    @DecimalMax(value = "80.0", message = "neckCm must be at most 80 cm")
    @JsonProperty("neckCm")
    @Schema(description = "Neck circumference in cm", example = "40.0", minimum = "20", maximum = "80")
    BigDecimal neckCm,

    @DecimalMin(value = "70.0", message = "shouldersCm must be at least 70 cm")
    @DecimalMax(value = "200.0", message = "shouldersCm must be at most 200 cm")
    @JsonProperty("shouldersCm")
    @Schema(description = "Shoulder width in cm", example = "120.0", minimum = "70", maximum = "200")
    BigDecimal shouldersCm,

    // Legs
    @DecimalMin(value = "20.0", message = "thighLeftCm must be at least 20 cm")
    @DecimalMax(value = "150.0", message = "thighLeftCm must be at most 150 cm")
    @JsonProperty("thighLeftCm")
    @Schema(description = "Left thigh circumference in cm", example = "58.0", minimum = "20", maximum = "150")
    BigDecimal thighLeftCm,

    @DecimalMin(value = "20.0", message = "thighRightCm must be at least 20 cm")
    @DecimalMax(value = "150.0", message = "thighRightCm must be at most 150 cm")
    @JsonProperty("thighRightCm")
    @Schema(description = "Right thigh circumference in cm", example = "58.5", minimum = "20", maximum = "150")
    BigDecimal thighRightCm,

    @DecimalMin(value = "15.0", message = "calfLeftCm must be at least 15 cm")
    @DecimalMax(value = "80.0", message = "calfLeftCm must be at most 80 cm")
    @JsonProperty("calfLeftCm")
    @Schema(description = "Left calf circumference in cm", example = "38.0", minimum = "15", maximum = "80")
    BigDecimal calfLeftCm,

    @DecimalMin(value = "15.0", message = "calfRightCm must be at least 15 cm")
    @DecimalMax(value = "80.0", message = "calfRightCm must be at most 80 cm")
    @JsonProperty("calfRightCm")
    @Schema(description = "Right calf circumference in cm", example = "38.5", minimum = "15", maximum = "80")
    BigDecimal calfRightCm,

    @Size(max = 500, message = "notes must be at most 500 characters")
    @JsonProperty("notes")
    @Schema(description = "Optional notes about the measurement", example = "Morning measurement, relaxed muscles")
    String notes
) implements EventPayload {}
