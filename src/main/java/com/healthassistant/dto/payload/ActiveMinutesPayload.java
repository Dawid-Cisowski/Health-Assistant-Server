package com.healthassistant.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(
    description = "Payload for ActiveMinutesRecorded.v1 - active minutes (moderate + vigorous activity) in a time bucket",
    example = """
        {
          "bucketStart": "2025-11-10T14:00:00Z",
          "bucketEnd": "2025-11-10T15:00:00Z",
          "activeMinutes": 25,
          "originPackage": "com.google.android.apps.fitness"
        }
        """
)
public record ActiveMinutesPayload(
    @NotNull(message = "bucketStart is required")
    @JsonProperty("bucketStart")
    @Schema(description = "Start of time bucket (ISO-8601 UTC)", example = "2025-11-10T14:00:00Z")
    Instant bucketStart,

    @NotNull(message = "bucketEnd is required")
    @JsonProperty("bucketEnd")
    @Schema(description = "End of time bucket (ISO-8601 UTC)", example = "2025-11-10T15:00:00Z")
    Instant bucketEnd,

    @NotNull(message = "activeMinutes is required")
    @Min(value = 0, message = "activeMinutes must be non-negative")
    @JsonProperty("activeMinutes")
    @Schema(description = "Active minutes (moderate + vigorous activity)", example = "25", minimum = "0")
    Integer activeMinutes,

    @NotBlank(message = "originPackage is required")
    @JsonProperty("originPackage")
    @Schema(description = "Source app package", example = "com.google.android.apps.fitness")
    String originPackage
) implements EventPayload {
}

