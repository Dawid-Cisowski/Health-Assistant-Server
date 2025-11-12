package com.healthassistant.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(
    description = "Payload for ActiveCaloriesBurnedRecorded.v1 - active calories burned in a time bucket",
    example = """
        {
          "bucketStart": "2025-11-10T13:00:00Z",
          "bucketEnd": "2025-11-10T14:00:00Z",
          "energyKcal": 125.5,
          "originPackage": "com.google.android.apps.fitness"
        }
        """
)
public record ActiveCaloriesPayload(
    @NotNull(message = "bucketStart is required")
    @JsonProperty("bucketStart")
    @Schema(description = "Start of time bucket (ISO-8601 UTC)", example = "2025-11-10T13:00:00Z")
    Instant bucketStart,

    @NotNull(message = "bucketEnd is required")
    @JsonProperty("bucketEnd")
    @Schema(description = "End of time bucket (ISO-8601 UTC)", example = "2025-11-10T14:00:00Z")
    Instant bucketEnd,

    @NotNull(message = "energyKcal is required")
    @Min(value = 0, message = "energyKcal must be non-negative")
    @JsonProperty("energyKcal")
    @Schema(description = "Active calories burned in kilocalories", example = "125.5", minimum = "0")
    Double energyKcal,

    @NotBlank(message = "originPackage is required")
    @JsonProperty("originPackage")
    @Schema(description = "Source app package", example = "com.google.android.apps.fitness")
    String originPackage
) implements EventPayload {
}

