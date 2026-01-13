package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Schema(
    description = "Payload for RestingHeartRateRecorded.v1 - daily resting heart rate measurement",
    example = """
        {
          "measuredAt": "2026-01-13T06:00:00Z",
          "restingBpm": 52,
          "originPackage": "com.google.android.apps.fitness"
        }
        """
)
public record RestingHeartRatePayload(
    @NotNull(message = "measuredAt is required")
    @JsonProperty("measuredAt")
    @Schema(description = "When the resting heart rate was measured (ISO-8601 UTC)", example = "2026-01-13T06:00:00Z")
    Instant measuredAt,

    @NotNull(message = "restingBpm is required")
    @Min(value = 30, message = "restingBpm must be at least 30")
    @Max(value = 120, message = "restingBpm must be at most 120 for resting measurements")
    @JsonProperty("restingBpm")
    @Schema(description = "Resting heart rate in BPM", example = "52", minimum = "30", maximum = "120")
    Integer restingBpm,

    @NotBlank(message = "originPackage is required")
    @JsonProperty("originPackage")
    @Schema(description = "Source app package", example = "com.google.android.apps.fitness")
    String originPackage
) implements EventPayload {
}
