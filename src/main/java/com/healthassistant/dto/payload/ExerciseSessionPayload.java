package com.healthassistant.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
    description = """
        Payload for ExerciseSessionRecorded.v1 - exercise session (🚧 Coming soon - logging only).
        
        Exercise types:
        - `other_0`: Walking (spacer)
        - Other types may include: running, cycling, etc.
        """,
    example = """
        {
          "sessionId": "e4210819-5708-3835-bcbb-2776e037e258",
          "type": "other_0",
          "start": "2025-11-11T09:03:15Z",
          "end": "2025-11-11T10:03:03Z",
          "durationMinutes": 59,
          "distanceMeters": "5838.7",
          "steps": 13812,
          "avgSpeedMetersPerSecond": "1.65",
          "avgHr": 83,
          "maxHr": 123,
          "originPackage": "com.heytap.health.international"
        }
        """
)
public record ExerciseSessionPayload(
    @JsonProperty("sessionId")
    @Schema(description = "Unique session identifier (UUID)", example = "e4210819-5708-3835-bcbb-2776e037e258")
    String sessionId,

    @JsonProperty("type")
    @Schema(
        description = "Exercise type identifier. Examples: 'other_0' (walking), 'running', 'cycling', etc.",
        example = "other_0"
    )
    String type,

    @JsonProperty("start")
    @Schema(description = "Exercise session start time (ISO-8601 UTC)", example = "2025-11-11T09:03:15Z")
    Instant start,

    @JsonProperty("end")
    @Schema(description = "Exercise session end time (ISO-8601 UTC)", example = "2025-11-11T10:03:03Z")
    Instant end,

    @JsonProperty("durationMinutes")
    @Schema(description = "Exercise duration in minutes", example = "59")
    Integer durationMinutes,

    @JsonProperty("distanceMeters")
    @Schema(description = "Distance covered in meters (as string)", example = "5838.7")
    String distanceMeters,

    @JsonProperty("steps")
    @Schema(description = "Number of steps during exercise", example = "13812")
    Integer steps,

    @JsonProperty("avgSpeedMetersPerSecond")
    @Schema(description = "Average speed in meters per second (as string)", example = "1.65")
    String avgSpeedMetersPerSecond,

    @JsonProperty("avgHr")
    @Schema(description = "Average heart rate in BPM", example = "83", nullable = true)
    Integer avgHr,

    @JsonProperty("maxHr")
    @Schema(description = "Maximum heart rate in BPM", example = "123", nullable = true)
    Integer maxHr,

    @JsonProperty("originPackage")
    @Schema(description = "Source app package", example = "com.heytap.health.international")
    String originPackage
) implements EventPayload {
}

