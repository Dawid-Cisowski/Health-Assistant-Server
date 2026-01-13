package com.healthassistant.weightimport.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Response from weight import operation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeightImportResponse(
    @JsonProperty("status")
    @Schema(description = "Import status: 'success' or 'failed'", example = "success")
    String status,

    @JsonProperty("measurementId")
    @Schema(description = "Generated measurement ID", example = "scale-import-2025-01-12-abc123")
    String measurementId,

    @JsonProperty("eventId")
    @Schema(description = "Event ID if stored successfully")
    String eventId,

    @JsonProperty("measurementDate")
    @Schema(description = "Date of measurement", example = "2025-01-12")
    LocalDate measurementDate,

    @JsonProperty("measuredAt")
    @Schema(description = "Timestamp of measurement")
    Instant measuredAt,

    @JsonProperty("score")
    @Schema(description = "Overall health score 0-100", example = "92")
    Integer score,

    @JsonProperty("weightKg")
    @Schema(description = "Body weight in kg", example = "72.6")
    BigDecimal weightKg,

    @JsonProperty("bmi")
    @Schema(description = "Body Mass Index", example = "23.4")
    BigDecimal bmi,

    @JsonProperty("bodyFatPercent")
    @Schema(description = "Body fat percentage", example = "21.0")
    BigDecimal bodyFatPercent,

    @JsonProperty("musclePercent")
    @Schema(description = "Muscle percentage", example = "52.1")
    BigDecimal musclePercent,

    @JsonProperty("hydrationPercent")
    @Schema(description = "Hydration percentage", example = "57.8")
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

    @JsonProperty("metabolicAge")
    @Schema(description = "Metabolic age in years", example = "31")
    Integer metabolicAge,

    @JsonProperty("confidence")
    @Schema(description = "AI extraction confidence 0-1", example = "0.95")
    Double confidence,

    @JsonProperty("overwrote")
    @Schema(description = "Whether an existing measurement was overwritten", example = "false")
    Boolean overwrote,

    @JsonProperty("errorMessage")
    @Schema(description = "Error message if failed")
    String errorMessage
) {

    public static WeightImportResponse success(
            String measurementId,
            String eventId,
            LocalDate measurementDate,
            Instant measuredAt,
            Integer score,
            BigDecimal weightKg,
            BigDecimal bmi,
            BigDecimal bodyFatPercent,
            BigDecimal musclePercent,
            BigDecimal hydrationPercent,
            BigDecimal boneMassKg,
            Integer bmrKcal,
            Integer visceralFatLevel,
            Integer metabolicAge,
            double confidence,
            boolean overwrote
    ) {
        return new WeightImportResponse(
                "success", measurementId, eventId, measurementDate, measuredAt,
                score, weightKg, bmi, bodyFatPercent, musclePercent, hydrationPercent,
                boneMassKg, bmrKcal, visceralFatLevel, metabolicAge, confidence, overwrote, null
        );
    }

    public static WeightImportResponse failure(String errorMessage) {
        return new WeightImportResponse(
                "failed", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, errorMessage
        );
    }
}
