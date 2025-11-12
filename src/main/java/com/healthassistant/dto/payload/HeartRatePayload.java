package com.healthassistant.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(
    description = "Payload for HeartRateSummaryRecorded.v1 - heart rate summary for a time bucket",
    example = """
        {
          "bucketStart": "2025-11-10T12:00:00Z",
          "bucketEnd": "2025-11-10T12:15:00Z",
          "avg": 78.3,
          "min": 61,
          "max": 115,
          "samples": 46,
          "originPackage": "com.google.android.apps.fitness"
        }
        """
)
public record HeartRatePayload(
    @NotNull(message = "bucketStart is required")
    @JsonProperty("bucketStart")
    @Schema(description = "Start of time bucket (ISO-8601 UTC)", example = "2025-11-10T12:00:00Z")
    Instant bucketStart,

    @NotNull(message = "bucketEnd is required")
    @JsonProperty("bucketEnd")
    @Schema(description = "End of time bucket (ISO-8601 UTC)", example = "2025-11-10T12:15:00Z")
    Instant bucketEnd,

    @NotNull(message = "avg is required")
    @Min(value = 0, message = "avg must be non-negative")
    @JsonProperty("avg")
    @Schema(description = "Average heart rate in BPM", example = "78.3", minimum = "0")
    Double avg,

    @NotNull(message = "min is required")
    @Min(value = 0, message = "min must be non-negative")
    @JsonProperty("min")
    @Schema(description = "Minimum heart rate in BPM", example = "61", minimum = "0")
    Integer min,

    @NotNull(message = "max is required")
    @Min(value = 0, message = "max must be non-negative")
    @JsonProperty("max")
    @Schema(description = "Maximum heart rate in BPM", example = "115", minimum = "0")
    Integer max,

    @NotNull(message = "samples is required")
    @Min(value = 1, message = "samples must be positive (>= 1)")
    @JsonProperty("samples")
    @Schema(description = "Number of heart rate samples", example = "46", minimum = "1")
    Integer samples,

    @NotBlank(message = "originPackage is required")
    @JsonProperty("originPackage")
    @Schema(description = "Source app package", example = "com.google.android.apps.fitness")
    String originPackage
) implements EventPayload {
}

