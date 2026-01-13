package com.healthassistant.weight.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Weight measurement with full body composition data")
public record WeightMeasurementResponse(
    @JsonProperty("measurementId")
    @Schema(description = "Unique measurement identifier", example = "weight-2025-01-12-abc123")
    String measurementId,

    @JsonProperty("date")
    @Schema(description = "Date of measurement", example = "2025-01-12")
    LocalDate date,

    @JsonProperty("measuredAt")
    @Schema(description = "Timestamp of measurement", example = "2025-01-12T07:30:00Z")
    Instant measuredAt,

    @JsonProperty("score")
    @Schema(description = "Overall health score (Wynik) 0-100", example = "92")
    Integer score,

    @JsonProperty("weightKg")
    @Schema(description = "Body weight in kilograms", example = "72.6")
    BigDecimal weightKg,

    @JsonProperty("bmi")
    @Schema(description = "Body Mass Index", example = "23.4")
    BigDecimal bmi,

    @JsonProperty("bodyFatPercent")
    @Schema(description = "Body fat percentage (BFR)", example = "21.0")
    BigDecimal bodyFatPercent,

    @JsonProperty("musclePercent")
    @Schema(description = "Muscle percentage", example = "52.1")
    BigDecimal musclePercent,

    @JsonProperty("hydrationPercent")
    @Schema(description = "Body hydration percentage", example = "57.8")
    BigDecimal hydrationPercent,

    @JsonProperty("boneMassKg")
    @Schema(description = "Bone mass in kg", example = "2.9")
    BigDecimal boneMassKg,

    @JsonProperty("bmrKcal")
    @Schema(description = "Basal Metabolic Rate in kcal", example = "1555")
    Integer bmrKcal,

    @JsonProperty("visceralFatLevel")
    @Schema(description = "Visceral fat level 1-59", example = "8")
    Integer visceralFatLevel,

    @JsonProperty("subcutaneousFatPercent")
    @Schema(description = "Subcutaneous fat percentage", example = "18.8")
    BigDecimal subcutaneousFatPercent,

    @JsonProperty("proteinPercent")
    @Schema(description = "Protein level percentage", example = "16.9")
    BigDecimal proteinPercent,

    @JsonProperty("metabolicAge")
    @Schema(description = "Metabolic/body age in years", example = "31")
    Integer metabolicAge,

    @JsonProperty("idealWeightKg")
    @Schema(description = "Ideal weight in kg", example = "67.2")
    BigDecimal idealWeightKg,

    @JsonProperty("weightControlKg")
    @Schema(description = "Weight control difference from ideal", example = "-5.4")
    BigDecimal weightControlKg,

    @JsonProperty("fatMassKg")
    @Schema(description = "Fat mass in kg", example = "15.2")
    BigDecimal fatMassKg,

    @JsonProperty("leanBodyMassKg")
    @Schema(description = "Lean body mass in kg", example = "57.4")
    BigDecimal leanBodyMassKg,

    @JsonProperty("muscleMassKg")
    @Schema(description = "Muscle mass in kg", example = "37.8")
    BigDecimal muscleMassKg,

    @JsonProperty("proteinMassKg")
    @Schema(description = "Protein mass in kg", example = "12.3")
    BigDecimal proteinMassKg,

    @JsonProperty("bodyType")
    @Schema(description = "Body type category", example = "STANDARD")
    String bodyType,

    @JsonProperty("source")
    @Schema(description = "Import source", example = "SCALE_SCREENSHOT")
    String source
) {}
