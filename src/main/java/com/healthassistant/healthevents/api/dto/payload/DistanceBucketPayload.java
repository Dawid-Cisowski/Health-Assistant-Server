package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(
    description = "Payload for DistanceBucketRecorded.v1 - distance traveled in a time bucket",
    example = """
        {
          "bucketStart": "2025-11-10T11:00:00Z",
          "bucketEnd": "2025-11-10T12:00:00Z",
          "distanceMeters": 1250.5,
          "originPackage": "com.google.android.apps.fitness"
        }
        """
)
public record DistanceBucketPayload(
    @NotNull(message = "bucketStart is required")
    @JsonProperty("bucketStart")
    @Schema(description = "Start of time bucket (ISO-8601 UTC)", example = "2025-11-10T11:00:00Z")
    Instant bucketStart,

    @NotNull(message = "bucketEnd is required")
    @JsonProperty("bucketEnd")
    @Schema(description = "End of time bucket (ISO-8601 UTC)", example = "2025-11-10T12:00:00Z")
    Instant bucketEnd,

    @NotNull(message = "distanceMeters is required")
    @Min(value = 0, message = "distanceMeters must be non-negative")
    @JsonProperty("distanceMeters")
    @Schema(description = "Distance in meters", example = "1250.5", minimum = "0")
    Double distanceMeters,

    @NotBlank(message = "originPackage is required")
    @JsonProperty("originPackage")
    @Schema(description = "Source app package", example = "com.google.android.apps.fitness")
    String originPackage
) implements EventPayload {
}
