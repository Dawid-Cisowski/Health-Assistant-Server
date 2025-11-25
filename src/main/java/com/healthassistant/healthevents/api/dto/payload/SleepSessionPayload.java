package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(
    description = "Payload for SleepSessionRecorded.v1 - sleep session with start/end times",
    example = """
        {
          "sleepId": "sleep-2025-11-10-session-1",
          "sleepStart": "2025-11-10T00:30:00Z",
          "sleepEnd": "2025-11-10T08:00:00Z",
          "totalMinutes": 450,
          "originPackage": "com.google.android.apps.fitness"
        }
        """
)
public record SleepSessionPayload(
    @NotBlank(message = "sleepId is required")
    @JsonProperty("sleepId")
    @Schema(description = "Unique identifier for this sleep session (used for idempotency)", example = "sleep-2025-11-10-session-1")
    String sleepId,

    @NotNull(message = "sleepStart is required")
    @JsonProperty("sleepStart")
    @Schema(description = "Sleep session start time (ISO-8601 UTC)", example = "2025-11-10T00:30:00Z")
    Instant sleepStart,

    @NotNull(message = "sleepEnd is required")
    @JsonProperty("sleepEnd")
    @Schema(description = "Sleep session end time (ISO-8601 UTC)", example = "2025-11-10T08:00:00Z")
    Instant sleepEnd,

    @NotNull(message = "totalMinutes is required")
    @Min(value = 0, message = "totalMinutes must be non-negative")
    @JsonProperty("totalMinutes")
    @Schema(description = "Total sleep duration in minutes", example = "450", minimum = "0")
    Integer totalMinutes,

    @NotBlank(message = "originPackage is required")
    @JsonProperty("originPackage")
    @Schema(description = "Source app package", example = "com.google.android.apps.fitness")
    String originPackage
) implements EventPayload {
}

