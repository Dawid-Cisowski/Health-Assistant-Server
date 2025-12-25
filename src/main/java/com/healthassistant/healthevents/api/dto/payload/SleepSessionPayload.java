package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(
    description = "Payload for SleepSessionRecorded.v1 - sleep session with start/end times and optional phases",
    example = """
        {
          "sleepId": "sleep-2025-11-10-session-1",
          "sleepStart": "2025-11-10T00:30:00Z",
          "sleepEnd": "2025-11-10T08:00:00Z",
          "totalMinutes": 450,
          "originPackage": "com.google.android.apps.fitness",
          "lightSleepMinutes": 180,
          "deepSleepMinutes": 120,
          "remSleepMinutes": 90,
          "awakeMinutes": 60,
          "sleepScore": 83,
          "source": "OHEALTH_SCREENSHOT"
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
    @Schema(description = "Source app package or import source", example = "com.google.android.apps.fitness")
    String originPackage,

    // Optional phase fields
    @Min(value = 0, message = "lightSleepMinutes must be non-negative")
    @JsonProperty("lightSleepMinutes")
    @Schema(description = "Minutes spent in light sleep phase (optional)", example = "180", minimum = "0")
    Integer lightSleepMinutes,

    @Min(value = 0, message = "deepSleepMinutes must be non-negative")
    @JsonProperty("deepSleepMinutes")
    @Schema(description = "Minutes spent in deep sleep phase (optional)", example = "120", minimum = "0")
    Integer deepSleepMinutes,

    @Min(value = 0, message = "remSleepMinutes must be non-negative")
    @JsonProperty("remSleepMinutes")
    @Schema(description = "Minutes spent in REM sleep phase (optional)", example = "90", minimum = "0")
    Integer remSleepMinutes,

    @Min(value = 0, message = "awakeMinutes must be non-negative")
    @JsonProperty("awakeMinutes")
    @Schema(description = "Minutes spent awake during sleep session (optional)", example = "60", minimum = "0")
    Integer awakeMinutes,

    @Min(value = 0, message = "sleepScore must be between 0 and 100")
    @Max(value = 100, message = "sleepScore must be between 0 and 100")
    @JsonProperty("sleepScore")
    @Schema(description = "Sleep quality score 0-100 (optional, auto-calculated if not provided)", example = "83", minimum = "0", maximum = "100")
    Integer sleepScore,

    @JsonProperty("source")
    @Schema(description = "Import source identifier (optional)", example = "OHEALTH_SCREENSHOT")
    String source
) implements EventPayload {

    public SleepSessionPayload(
            String sleepId,
            Instant sleepStart,
            Instant sleepEnd,
            Integer totalMinutes,
            String originPackage
    ) {
        this(sleepId, sleepStart, sleepEnd, totalMinutes, originPackage,
                null, null, null, null, null, null);
    }

}

