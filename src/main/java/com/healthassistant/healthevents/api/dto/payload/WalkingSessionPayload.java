package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
    description = """
        Payload for WalkingSessionRecorded.v1 - walking session with aggregated data from Google Fit.
        
        Contains session metadata and aggregated metrics (steps, distance, calories, heart rate) 
        fetched from Google Fit aggregate API with 1-minute buckets.
        """,
    example = """
        {
          "sessionId": "health_platform:888c4cb9-1034-324e-b093-8a260f19e7a5",
          "name": "Chodzenie",
          "start": "2025-11-11T09:03:15Z",
          "end": "2025-11-11T10:03:03Z",
          "durationMinutes": 59,
          "totalSteps": 13812,
          "totalDistanceMeters": 5838.7,
          "totalCalories": 125,
          "avgHeartRate": 83,
          "maxHeartRate": 123,
          "heartRateSamples": [75, 80, 85, 90, 88],
          "originPackage": "com.heytap.health.international"
        }
        """
)
public record WalkingSessionPayload(
    @JsonProperty("sessionId")
    @Schema(description = "Unique session identifier from Google Fit", example = "health_platform:888c4cb9-1034-324e-b093-8a260f19e7a5")
    String sessionId,

    @JsonProperty("name")
    @Schema(description = "Session name from Google Fit", example = "Chodzenie", nullable = true)
    String name,

    @JsonProperty("start")
    @Schema(description = "Walking session start time (ISO-8601 UTC)", example = "2025-11-11T09:03:15Z")
    Instant start,

    @JsonProperty("end")
    @Schema(description = "Walking session end time (ISO-8601 UTC)", example = "2025-11-11T10:03:03Z")
    Instant end,

    @JsonProperty("durationMinutes")
    @Schema(description = "Walking duration in minutes", example = "59")
    Integer durationMinutes,

    @JsonProperty("totalSteps")
    @Schema(description = "Total steps during walking session", example = "13812", nullable = true)
    Integer totalSteps,

    @JsonProperty("totalDistanceMeters")
    @Schema(description = "Total distance covered in meters (rounded to nearest meter)", example = "5839", nullable = true)
    Long totalDistanceMeters,

    @JsonProperty("totalCalories")
    @Schema(description = "Total calories burned during walking session", example = "125", nullable = true)
    Integer totalCalories,

    @JsonProperty("avgHeartRate")
    @Schema(description = "Average heart rate in BPM", example = "83", nullable = true)
    Integer avgHeartRate,

    @JsonProperty("maxHeartRate")
    @Schema(description = "Maximum heart rate in BPM", example = "123", nullable = true)
    Integer maxHeartRate,

    @JsonProperty("heartRateSamples")
    @Schema(description = "List of heart rate samples from 1-minute buckets", example = "[75, 80, 85, 90, 88]", nullable = true)
    List<Integer> heartRateSamples,

    @JsonProperty("originPackage")
    @Schema(description = "Source app package", example = "com.heytap.health.international")
    String originPackage
) implements EventPayload {
}

