package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(
    description = "Payload for WeightMeasurementRecorded.v1 - smart scale body composition measurement",
    example = """
        {
          "measurementId": "weight-2025-01-12-abc123",
          "measuredAt": "2025-01-12T07:30:00Z",
          "score": 92,
          "weightKg": 72.6,
          "bmi": 23.4,
          "bodyFatPercent": 21.0,
          "musclePercent": 52.1,
          "hydrationPercent": 57.8,
          "boneMassKg": 2.9,
          "bmrKcal": 1555,
          "visceralFatLevel": 8,
          "subcutaneousFatPercent": 18.8,
          "proteinPercent": 16.9,
          "metabolicAge": 31,
          "idealWeightKg": 67.2,
          "weightControlKg": -5.4,
          "fatMassKg": 15.2,
          "leanBodyMassKg": 57.4,
          "muscleMassKg": 37.8,
          "proteinMassKg": 12.3,
          "bodyType": "STANDARD",
          "source": "SCALE_SCREENSHOT"
        }
        """
)
public record WeightMeasurementPayload(
    @NotBlank(message = "measurementId is required")
    @JsonProperty("measurementId")
    @Schema(description = "Unique identifier for this measurement", example = "weight-2025-01-12-abc123")
    String measurementId,

    @NotNull(message = "measuredAt is required")
    @JsonProperty("measuredAt")
    @Schema(description = "Timestamp when measurement was taken (ISO-8601 UTC)", example = "2025-01-12T07:30:00Z")
    Instant measuredAt,

    @Min(value = 0, message = "score must be between 0 and 100")
    @Max(value = 100, message = "score must be between 0 and 100")
    @JsonProperty("score")
    @Schema(description = "Overall health score (Wynik) 0-100", example = "92", minimum = "0", maximum = "100")
    Integer score,

    @NotNull(message = "weightKg is required")
    @DecimalMin(value = "1.0", message = "weightKg must be at least 1 kg")
    @DecimalMax(value = "500.0", message = "weightKg must be at most 500 kg")
    @JsonProperty("weightKg")
    @Schema(description = "Body weight in kilograms (Waga)", example = "72.6")
    BigDecimal weightKg,

    @DecimalMin(value = "1.0", message = "bmi must be at least 1")
    @DecimalMax(value = "100.0", message = "bmi must be at most 100")
    @JsonProperty("bmi")
    @Schema(description = "Body Mass Index", example = "23.4")
    BigDecimal bmi,

    @DecimalMin(value = "0.0", message = "bodyFatPercent must be between 0 and 100")
    @DecimalMax(value = "100.0", message = "bodyFatPercent must be between 0 and 100")
    @JsonProperty("bodyFatPercent")
    @Schema(description = "Body fat percentage (BFR)", example = "21.0")
    BigDecimal bodyFatPercent,

    @DecimalMin(value = "0.0", message = "musclePercent must be between 0 and 100")
    @DecimalMax(value = "100.0", message = "musclePercent must be between 0 and 100")
    @JsonProperty("musclePercent")
    @Schema(description = "Muscle percentage (Miesnie)", example = "52.1")
    BigDecimal musclePercent,

    @DecimalMin(value = "0.0", message = "hydrationPercent must be between 0 and 100")
    @DecimalMax(value = "100.0", message = "hydrationPercent must be between 0 and 100")
    @JsonProperty("hydrationPercent")
    @Schema(description = "Body hydration percentage (Nawodnienie)", example = "57.8")
    BigDecimal hydrationPercent,

    @DecimalMin(value = "0.0", message = "boneMassKg must be non-negative")
    @DecimalMax(value = "50.0", message = "boneMassKg must be at most 50 kg")
    @JsonProperty("boneMassKg")
    @Schema(description = "Bone mass in kilograms (Masa kostna)", example = "2.9")
    BigDecimal boneMassKg,

    @Min(value = 0, message = "bmrKcal must be non-negative")
    @Max(value = 10000, message = "bmrKcal must be at most 10000")
    @JsonProperty("bmrKcal")
    @Schema(description = "Basal Metabolic Rate in kcal (BMR)", example = "1555")
    Integer bmrKcal,

    @Min(value = 1, message = "visceralFatLevel must be between 1 and 59")
    @Max(value = 59, message = "visceralFatLevel must be between 1 and 59")
    @JsonProperty("visceralFatLevel")
    @Schema(description = "Visceral fat level 1-59 (Tluszcz trzewny)", example = "8", minimum = "1", maximum = "59")
    Integer visceralFatLevel,

    @DecimalMin(value = "0.0", message = "subcutaneousFatPercent must be between 0 and 100")
    @DecimalMax(value = "100.0", message = "subcutaneousFatPercent must be between 0 and 100")
    @JsonProperty("subcutaneousFatPercent")
    @Schema(description = "Subcutaneous fat percentage (Tluszcz podskórny)", example = "18.8")
    BigDecimal subcutaneousFatPercent,

    @DecimalMin(value = "0.0", message = "proteinPercent must be between 0 and 100")
    @DecimalMax(value = "100.0", message = "proteinPercent must be between 0 and 100")
    @JsonProperty("proteinPercent")
    @Schema(description = "Protein level percentage (Poziom bialka)", example = "16.9")
    BigDecimal proteinPercent,

    @Min(value = 1, message = "metabolicAge must be at least 1")
    @Max(value = 150, message = "metabolicAge must be at most 150")
    @JsonProperty("metabolicAge")
    @Schema(description = "Metabolic/body age in years (Wiek ciala)", example = "31")
    Integer metabolicAge,

    @DecimalMin(value = "1.0", message = "idealWeightKg must be at least 1 kg")
    @DecimalMax(value = "500.0", message = "idealWeightKg must be at most 500 kg")
    @JsonProperty("idealWeightKg")
    @Schema(description = "Ideal/standard weight in kilograms (Standardowa waga)", example = "67.2")
    BigDecimal idealWeightKg,

    @JsonProperty("weightControlKg")
    @Schema(description = "Weight control difference in kg - negative means need to lose (Kontrola wagi)", example = "-5.4")
    BigDecimal weightControlKg,

    @DecimalMin(value = "0.0", message = "fatMassKg must be non-negative")
    @DecimalMax(value = "300.0", message = "fatMassKg must be at most 300 kg")
    @JsonProperty("fatMassKg")
    @Schema(description = "Fat mass in kilograms (Tluszcz)", example = "15.2")
    BigDecimal fatMassKg,

    @DecimalMin(value = "0.0", message = "leanBodyMassKg must be non-negative")
    @DecimalMax(value = "300.0", message = "leanBodyMassKg must be at most 300 kg")
    @JsonProperty("leanBodyMassKg")
    @Schema(description = "Lean body mass / fat-free mass in kilograms (Waga bez tluszczu)", example = "57.4")
    BigDecimal leanBodyMassKg,

    @DecimalMin(value = "0.0", message = "muscleMassKg must be non-negative")
    @DecimalMax(value = "200.0", message = "muscleMassKg must be at most 200 kg")
    @JsonProperty("muscleMassKg")
    @Schema(description = "Muscle mass in kilograms (Masa miesni)", example = "37.8")
    BigDecimal muscleMassKg,

    @DecimalMin(value = "0.0", message = "proteinMassKg must be non-negative")
    @DecimalMax(value = "100.0", message = "proteinMassKg must be at most 100 kg")
    @JsonProperty("proteinMassKg")
    @Schema(description = "Protein mass in kilograms (Masa bialkowa)", example = "12.3")
    BigDecimal proteinMassKg,

    @JsonProperty("bodyType")
    @Schema(description = "Body type category (Typ ciala)", example = "STANDARD")
    String bodyType,

    @JsonProperty("source")
    @Schema(description = "Import source identifier", example = "SCALE_SCREENSHOT")
    String source
) implements EventPayload {}
